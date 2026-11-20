package com.example.EPustok.chat;

import com.example.EPustok.identity.User;
import com.example.EPustok.identity.UserRepository;
import com.example.EPustok.market.ResellListing;
import com.example.EPustok.market.ResellListings;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class ChatService {
    private final ExchangeConversations conversations;
    private final ExchangeChatMessages messages;
    private final ResellListings listings;
    private final UserRepository users;

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
        return conversations.findByListingIdAndOffererId(listingId, currentUser.getId())
                .orElseGet(() -> conversations.save(
                        new ExchangeConversation(listingId, listing.getUserId(), currentUser.getId())));
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

    @Transactional(readOnly = true)
    public List<ExchangeConversation> userConversations(String email) {
        User currentUser = requireUser(email);
        return conversations.findByOwnerIdOrOffererIdOrderByCreatedAtDesc(currentUser.getId(), currentUser.getId());
    }

    @Transactional(readOnly = true)
    public List<ChatMessageView> history(String email, long conversationId) {
        getAuthorizedConversation(email, conversationId);
        Map<Long, String> names = users.findAll().stream()
                .collect(Collectors.toMap(User::getId, User::getName, (a, b) -> a));
        return messages.findByConversationIdOrderBySentAtAsc(conversationId).stream()
                .map(m -> new ChatMessageView(
                        m.getId(),
                        m.getConversationId(),
                        m.getSenderId(),
                        names.getOrDefault(m.getSenderId(), "Reader"),
                        m.getContent(),
                        m.getSentAt()))
                .toList();
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
        ExchangeChatMessage saved = messages.save(new ExchangeChatMessage(conversationId, sender.getId(), content));
        return new ChatMessageView(
                saved.getId(),
                saved.getConversationId(),
                saved.getSenderId(),
                sender.getName(),
                saved.getContent(),
                saved.getSentAt());
    }

    public record ChatMessageView(
            Long id,
            Long conversationId,
            Long senderId,
            String senderName,
            String content,
            Instant sentAt) {}
}
