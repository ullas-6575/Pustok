package com.example.EPustok.chat;

import com.example.EPustok.identity.User;
import com.example.EPustok.identity.UserRepository;
import com.example.EPustok.market.ResellListing;
import com.example.EPustok.market.ResellListings;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Handles 2-user exchange negotiation chat views and real-time STOMP WebSocket messaging.
 */
@Controller
public class ChatController {
    private final ChatService chatService;
    private final ResellListings listings;
    private final UserRepository users;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatController(
            ChatService chatService,
            ResellListings listings,
            UserRepository users,
            SimpMessagingTemplate messagingTemplate) {
        this.chatService = chatService;
        this.listings = listings;
        this.users = users;
        this.messagingTemplate = messagingTemplate;
    }

    public record ChatPayload(String content) {}

    public record ConversationSummary(
            Long id,
            Long listingId,
            String bookTitle,
            String desiredBook,
            String listingStatus,
            String peerName) {}

    @PostMapping("/chat/exchange/{listingId}")
    public String startExchangeChat(Authentication authentication, @PathVariable long listingId) {
        ExchangeConversation conversation = chatService.openOrGetConversation(authentication.getName(), listingId);
        return "redirect:/chat/" + conversation.getId();
    }

    @GetMapping("/chat")
    public String inbox(Authentication authentication, Model model) {
        User currentUser = chatService.requireUser(authentication.getName());
        List<ConversationSummary> summaries = buildSummaries(currentUser, chatService.userConversations(authentication.getName()));
        String email = authentication.getName();

        model.addAttribute("conversations", summaries);
        model.addAttribute("activeConversation", null);
        model.addAttribute("activeListing", null);
        model.addAttribute("peerName", null);
        model.addAttribute("messages", List.of());
        model.addAttribute("currentUserId", currentUser.getId());
        model.addAttribute("currentUserName", currentUser.getName());
        model.addAttribute("displayName", email.contains("@") ? email.substring(0, email.indexOf('@')) : email);
        return "chat";
    }

    @GetMapping("/chat/{conversationId}")
    public String conversation(
            Authentication authentication,
            @PathVariable long conversationId,
            Model model) {
        User currentUser = chatService.requireUser(authentication.getName());
        ExchangeConversation active = chatService.getAuthorizedConversation(authentication.getName(), conversationId);
        ResellListing activeListing = listings.findById(active.getListingId()).orElse(null);

        Map<Long, String> userNames = users.findAll().stream()
                .collect(Collectors.toMap(User::getId, User::getName, (a, b) -> a));
        Long peerId = active.getOwnerId().equals(currentUser.getId()) ? active.getOffererId() : active.getOwnerId();
        String peerName = userNames.getOrDefault(peerId, "Reader");

        List<ConversationSummary> summaries = buildSummaries(currentUser, chatService.userConversations(authentication.getName()));
        String email = authentication.getName();

        model.addAttribute("conversations", summaries);
        model.addAttribute("activeConversation", active);
        model.addAttribute("activeListing", activeListing);
        model.addAttribute("peerName", peerName);
        model.addAttribute("messages", chatService.history(authentication.getName(), conversationId));
        model.addAttribute("currentUserId", currentUser.getId());
        model.addAttribute("currentUserName", currentUser.getName());
        model.addAttribute("isListingOwner", active.getOwnerId().equals(currentUser.getId()));
        model.addAttribute("displayName", email.contains("@") ? email.substring(0, email.indexOf('@')) : email);
        return "chat";
    }

    @MessageMapping("/chat/{conversationId}")
    public void handleMessage(
            @DestinationVariable long conversationId,
            @Payload ChatPayload payload,
            Principal principal) {
        if (principal == null || payload == null) {
            return;
        }
        ChatService.ChatMessageView saved = chatService.sendMessage(principal.getName(), conversationId, payload.content());
        messagingTemplate.convertAndSend("/topic/chat/" + conversationId, saved);
    }

    private List<ConversationSummary> buildSummaries(User currentUser, List<ExchangeConversation> list) {
        Map<Long, String> userNames = users.findAll().stream()
                .collect(Collectors.toMap(User::getId, User::getName, (a, b) -> a));
        Map<Long, ResellListing> listingMap = listings.findAll().stream()
                .collect(Collectors.toMap(ResellListing::getId, l -> l, (a, b) -> a));

        return list.stream().map(conv -> {
            ResellListing listing = listingMap.get(conv.getListingId());
            Long peerId = conv.getOwnerId().equals(currentUser.getId()) ? conv.getOffererId() : conv.getOwnerId();
            return new ConversationSummary(
                    conv.getId(),
                    conv.getListingId(),
                    listing != null ? listing.getTitle() : "Book #" + conv.getListingId(),
                    listing != null && listing.getDesiredBook() != null ? listing.getDesiredBook() : "Open to offers",
                    listing != null ? listing.getStatus() : "AVAILABLE",
                    userNames.getOrDefault(peerId, "Reader"));
        }).toList();
    }
}
