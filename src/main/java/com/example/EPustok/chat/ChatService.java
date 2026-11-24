package com.example.EPustok.chat;

import com.example.EPustok.identity.User;
import com.example.EPustok.identity.UserRepository;
import com.example.EPustok.market.ResellListing;
import com.example.EPustok.market.ResellListings;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class ChatService {
    /** At most this many messages per user inside each window. */
    static final int MAX_MESSAGES_PER_WINDOW = 5;
    static final long RATE_WINDOW_MILLIS = 10_000L;

    private final ExchangeConversations conversations;
    private final ExchangeChatMessages messages;
    private final ResellListings listings;
    private final UserRepository users;
    /** Recent send timestamps (epoch millis) per user id. In-memory, so the limit is per application instance. */
    private final Map<Long, Deque<Long>> recentSends = new ConcurrentHashMap<>();

    public ChatService(
            ExchangeConversations conversations,
            ExchangeChatMessages messages,
            ResellListings listings,
            UserRepository users) {
        this.conversations = conversations;
        this.messages = messages;
        this.listings = listings;
        this.users = users;
    }

    public User requireUser(String email) {
        return users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    /**
     * Runs without an outer transaction so that a race on the unique (listing_id, offerer_id) constraint only
     * rolls back the failed insert. The losing request then re-reads the row that the winning request created.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ExchangeConversation openOrGetConversation(String email, long listingId) {
        User currentUser = requireUser(email);
        ResellListing listing = listings.findById(listingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));
        if (!"EXCHANGE".equals(listing.getListingType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chat negotiation is only for exchange listings");
        }
        if (listing.getUserId().equals(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot start an exchange chat with yourself");
        }
        Optional<ExchangeConversation> existing = conversations.findByListingIdAndOffererId(listingId, currentUser.getId());
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return conversations.save(new ExchangeConversation(listingId, listing.getUserId(), currentUser.getId()));
        } catch (DataIntegrityViolationException raced) {
            return conversations.findByListingIdAndOffererId(listingId, currentUser.getId())
                    .orElseThrow(() -> raced);
        }
    }

    @Transactional(readOnly = true)
    public ExchangeConversation getAuthorizedConversation(String email, long conversationId) {
        User currentUser = requireUser(email);
        ExchangeConversation conversation = conversations.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
        if (!conversation.includesUser(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this chat");
        }
        return conversation;
    }

    /** Boolean form of the participant check, used by the STOMP SUBSCRIBE guard. Unknown users and conversations return false. */
    @Transactional(readOnly = true)
    public boolean isParticipant(String email, long conversationId) {
        return users.findByEmailIgnoreCase(email)
                .map(User::getId)
                .flatMap(userId -> conversations.findById(conversationId).map(c -> c.includesUser(userId)))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<ExchangeConversation> userConversations(String email) {
        User currentUser = requireUser(email);
        return conversations.findByOwnerIdOrOffererIdOrderByCreatedAtDesc(currentUser.getId(), currentUser.getId());
    }

    @Transactional(readOnly = true)
    public List<ChatMessageView> history(String email, long conversationId) {
        getAuthorizedConversation(email, conversationId);
        List<ExchangeChatMessage> rows = messages.findByConversationIdOrderBySentAtAsc(conversationId);
        Map<Long, String> names = displayNames(rows.stream().map(ExchangeChatMessage::getSenderId).distinct().toList());
        return rows.stream()
                .map(m -> toView(m, names.getOrDefault(m.getSenderId(), "Reader")))
                .toList();
    }

    /** Display names for the given user ids only. Avoids loading every user. */
    @Transactional(readOnly = true)
    public Map<Long, String> displayNames(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return users.findNamesByIdIn(userIds).stream()
                .collect(Collectors.toMap(UserRepository.UserName::getId, UserRepository.UserName::getName, (a, b) -> a));
    }

    public ChatMessageView sendMessage(String email, long conversationId, String rawContent) {
        User sender = requireUser(email);
        ExchangeConversation conversation = conversations.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
        if (!conversation.includesUser(sender.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this chat");
        }
        String content = rawContent == null ? "" : rawContent.trim();
        if (content.isEmpty() || content.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message must be between 1 and 1000 characters");
        }
        reserveSendSlot(sender.getId(), System.currentTimeMillis());
        ExchangeChatMessage saved = messages.save(new ExchangeChatMessage(conversationId, sender.getId(), content));
        return toView(saved, sender.getName());
    }

    /**
     * Sliding-window limit: a user may send at most MAX_MESSAGES_PER_WINDOW messages in any RATE_WINDOW_MILLIS span.
     * Throws 429 when the limit is reached; otherwise records the send.
     */
    void reserveSendSlot(long userId, long nowMillis) {
        Deque<Long> window = recentSends.computeIfAbsent(userId, id -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && nowMillis - window.peekFirst() >= RATE_WINDOW_MILLIS) {
                window.pollFirst();
            }
            if (window.size() >= MAX_MESSAGES_PER_WINDOW) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "You are sending messages too quickly. Wait a few seconds and try again.");
            }
            window.addLast(nowMillis);
        }
    }

    private static ChatMessageView toView(ExchangeChatMessage m, String senderName) {
        return new ChatMessageView(
                m.getId(),
                m.getConversationId(),
                m.getSenderId(),
                senderName,
                m.getContent(),
                m.getSentAt());
    }

    public record ChatMessageView(
            Long id,
            Long conversationId,
            Long senderId,
            String senderName,
            String content,
            Instant sentAt) {}
}
