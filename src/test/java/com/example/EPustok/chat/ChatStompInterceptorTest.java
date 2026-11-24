package com.example.EPustok.chat;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatStompInterceptorTest {
    private static final String OFFERER = "offerer@example.com";
    private static final String OUTSIDER = "outsider@example.com";

    private ChatService chatService;
    private JdbcTemplate jdbc;
    private ChannelInterceptor inbound;
    private ChannelInterceptor outbound;

    @BeforeEach
    void setUp() {
        chatService = mock(ChatService.class);
        jdbc = mock(JdbcTemplate.class);
        ChatStompInterceptor guard = new ChatStompInterceptor(chatService, jdbc);
        inbound = guard.inboundInterceptor();
        outbound = guard.outboundInterceptor();
        // Token "jti-1" is not revoked
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq("jti-1"))).thenReturn(0L);
    }

    private static Map<String, Object> handshake(String jti) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(ChatHandshakeInterceptor.JTI_ATTRIBUTE, jti);
        attributes.put(ChatHandshakeInterceptor.EXPIRES_AT_ATTRIBUTE, Instant.now().plusSeconds(3600));
        return attributes;
    }

    private static Message<byte[]> stomp(StompCommand command, String sessionId, Map<String, Object> attributes,
                                         String user, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId(sessionId);
        accessor.setSessionAttributes(attributes);
        if (user != null) {
            accessor.setUser(() -> user);
        }
        if (destination != null) {
            accessor.setDestination(destination);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static Message<byte[]> brokerMessage(String sessionId, String destination) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        accessor.setSessionId(sessionId);
        accessor.setDestination(destination);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void subscribeByParticipantIsAllowed() {
        when(chatService.isParticipant(OFFERER, 99L)).thenReturn(true);
        Message<byte[]> subscribe = stomp(StompCommand.SUBSCRIBE, "s1", handshake("jti-1"), OFFERER, "/topic/chat/99");

        assertSame(subscribe, inbound.preSend(subscribe, null));
    }

    @Test
    void subscribeByNonParticipantIsRejected() {
        when(chatService.isParticipant(OUTSIDER, 99L)).thenReturn(false);
        Message<byte[]> subscribe = stomp(StompCommand.SUBSCRIBE, "s2", handshake("jti-1"), OUTSIDER, "/topic/chat/99");

        assertThrows(MessageDeliveryException.class, () -> inbound.preSend(subscribe, null));
    }

    @Test
    void malformedConversationIdsAreRejectedWithoutAnyLookup() {
        List<String> destinations = List.of(
                "/topic/chat/", "/topic/chat/0", "/topic/chat/007", "/topic/chat/12x",
                "/topic/chat/*", "/topic/chat/99/extra");
        for (String destination : destinations) {
            Message<byte[]> subscribe = stomp(StompCommand.SUBSCRIBE, "s1", handshake("jti-1"), OFFERER, destination);
            assertThrows(MessageDeliveryException.class, () -> inbound.preSend(subscribe, null), destination);
        }
        verify(chatService, never()).isParticipant(anyString(), anyLong());
    }

    @Test
    void clientSendToBrokerTopicIsRejectedButAppSendPasses() {
        Message<byte[]> topicSend = stomp(StompCommand.SEND, "s1", handshake("jti-1"), OFFERER, "/topic/chat/99");
        assertThrows(MessageDeliveryException.class, () -> inbound.preSend(topicSend, null));

        Message<byte[]> appSend = stomp(StompCommand.SEND, "s1", handshake("jti-1"), OFFERER, "/app/chat/99");
        assertSame(appSend, inbound.preSend(appSend, null));
    }

    @Test
    void revokedTokenCannotSendOrSubscribe() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq("jti-revoked"))).thenReturn(1L);
        Message<byte[]> send = stomp(StompCommand.SEND, "s3", handshake("jti-revoked"), OFFERER, "/app/chat/99");
        Message<byte[]> subscribe = stomp(StompCommand.SUBSCRIBE, "s3", handshake("jti-revoked"), OFFERER, "/topic/chat/99");

        assertThrows(MessageDeliveryException.class, () -> inbound.preSend(send, null));
        assertThrows(MessageDeliveryException.class, () -> inbound.preSend(subscribe, null));
    }

    @Test
    void expiredHandshakeTokenIsRejected() {
        Map<String, Object> attributes = handshake("jti-1");
        attributes.put(ChatHandshakeInterceptor.EXPIRES_AT_ATTRIBUTE, Instant.now().minusSeconds(1));
        Message<byte[]> send = stomp(StompCommand.SEND, "s4", attributes, OFFERER, "/app/chat/99");

        assertThrows(MessageDeliveryException.class, () -> inbound.preSend(send, null));
    }

    @Test
    void sessionWithoutHandshakeTokenIsRejected() {
        Message<byte[]> send = stomp(StompCommand.SEND, "s5", new HashMap<>(), OFFERER, "/app/chat/99");

        assertThrows(MessageDeliveryException.class, () -> inbound.preSend(send, null));
    }

    @Test
    void outboundChatMessagesStopOnceTheTokenIsRevoked() {
        inbound.preSend(stomp(StompCommand.CONNECT, "s6", handshake("jti-1"), OFFERER, null), null);

        Message<byte[]> live = brokerMessage("s6", "/topic/chat/99");
        assertSame(live, outbound.preSend(live, null));

        when(jdbc.queryForObject(anyString(), eq(Long.class), eq("jti-1"))).thenReturn(1L);
        assertNull(outbound.preSend(brokerMessage("s6", "/topic/chat/99"), null));
    }
}
