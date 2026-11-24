package com.example.EPustok.chat;

import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * STOMP-level authorization for the chat broker.
 *
 * Inbound (client to server): SUBSCRIBE to /topic/chat/{id} requires the user to be a participant. SEND is allowed
 * only to /app/chat/** (the controller re-checks participation). Both require a non-revoked, unexpired token.
 * Other inbound frames pass through.
 *
 * Outbound (server to client): MESSAGE frames for /topic/chat/** are dropped once the session's token is revoked
 * or expired, so a logged-out client stops receiving live messages.
 */
@Component
public class ChatStompInterceptor {
    private static final String TOPIC_CHAT_PREFIX = "/topic/chat";
    private static final String TOPIC_CHAT_PATH = "/topic/chat/";
    private static final String APP_CHAT_PATH = "/app/chat/";
    private static final Pattern CANONICAL_ID = Pattern.compile("[1-9]\\d{0,17}");

    private final ChatService chatService;
    private final JdbcTemplate jdbc;
    /** Session id to token details, filled from every inbound frame so outbound frames can be checked. */
    private final Map<String, Authorization> sessions = new ConcurrentHashMap<>();

    public ChatStompInterceptor(ChatService chatService, JdbcTemplate jdbc) {
        this.chatService = chatService;
        this.jdbc = jdbc;
    }

    public ChannelInterceptor inboundInterceptor() {
        return new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                return checkInbound(message);
            }
        };
    }

    public ChannelInterceptor outboundInterceptor() {
        return new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                return filterOutbound(message);
            }
        };
    }

    @EventListener
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }

    private Message<?> checkInbound(Message<?> message) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();
        if (command == null) {
            return message;
        }
        String sessionId = accessor.getSessionId();
        Authorization fromHandshake = authorizationOf(accessor.getSessionAttributes());
        if (sessionId != null && fromHandshake != null) {
            sessions.put(sessionId, fromHandshake);
        }
        if (command != StompCommand.SUBSCRIBE && command != StompCommand.SEND) {
            return message;
        }
        if (fromHandshake == null || !isActive(fromHandshake)) {
            throw deny("Your chat session has ended. Sign in again.");
        }

        String destination = accessor.getDestination();
        if (command == StompCommand.SEND) {
            // Client SEND to a broker topic would be fanned out to subscribers without any check.
            if (destination == null || !destination.startsWith(APP_CHAT_PATH)) {
                throw deny("This destination is not allowed.");
            }
            return message;
        }

        if (destination != null && destination.startsWith(TOPIC_CHAT_PREFIX)) {
            long conversationId = parseConversationId(destination);
            Principal user = accessor.getUser();
            if (user == null || !chatService.isParticipant(user.getName(), conversationId)) {
                throw deny("You do not have access to this chat.");
            }
        }
        return message;
    }

    private Message<?> filterOutbound(Message<?> message) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.wrap(message);
        if (accessor.getMessageType() != SimpMessageType.MESSAGE) {
            return message;
        }
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(TOPIC_CHAT_PREFIX)) {
            return message;
        }
        String sessionId = accessor.getSessionId();
        Authorization auth = sessionId == null ? null : sessions.get(sessionId);
        if (auth == null || !isActive(auth)) {
            return null; // Returning null drops the message for this subscriber.
        }
        return message;
    }

    private static long parseConversationId(String destination) {
        if (!destination.startsWith(TOPIC_CHAT_PATH)) {
            throw deny("Invalid chat destination.");
        }
        String id = destination.substring(TOPIC_CHAT_PATH.length());
        if (!CANONICAL_ID.matcher(id).matches()) {
            throw deny("Invalid chat destination.");
        }
        return Long.parseLong(id);
    }

    private boolean isActive(Authorization auth) {
        if (auth.expiresAt() == null || !auth.expiresAt().isAfter(Instant.now())) {
            return false;
        }
        Long revoked = jdbc.queryForObject(
                "select count(*) from identity_revoked_tokens where token_id = ?", Long.class, auth.jti());
        return revoked != null && revoked == 0L;
    }

    private static Authorization authorizationOf(Map<String, Object> attributes) {
        if (attributes == null) {
            return null;
        }
        Object jti = attributes.get(ChatHandshakeInterceptor.JTI_ATTRIBUTE);
        Object expiresAt = attributes.get(ChatHandshakeInterceptor.EXPIRES_AT_ATTRIBUTE);
        if (jti instanceof String id && expiresAt instanceof Instant expiry) {
            return new Authorization(id, expiry);
        }
        return null;
    }

    private static MessageDeliveryException deny(String reason) {
        return new MessageDeliveryException(reason);
    }

    private record Authorization(String jti, Instant expiresAt) {}
}
