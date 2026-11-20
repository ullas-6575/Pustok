package com.example.EPustok.chat;

import com.example.EPustok.identity.User;
import com.example.EPustok.identity.UserRepository;
import com.example.EPustok.market.ResellListing;
import com.example.EPustok.market.ResellListings;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatServiceTest {

    private User userWithId(long id, String name, String email) throws Exception {
        User u = new User(name, email, "hash");
        var f = User.class.getDeclaredField("id");
        f.setAccessible(true);
        f.set(u, id);
        return u;
    }

    @Test
    void opensTwoUserConversationAndEnforcesParticipantAccess() throws Exception {
        ExchangeConversations conversations = mock(ExchangeConversations.class);
        ExchangeChatMessages messages = mock(ExchangeChatMessages.class);
        ResellListings listings = mock(ResellListings.class);
        UserRepository users = mock(UserRepository.class);

        User owner = userWithId(1L, "Owner", "owner@example.com");
        User offerer = userWithId(2L, "Offerer", "offerer@example.com");
        User outsider = userWithId(3L, "Outsider", "outsider@example.com");

        ResellListing exchangeListing = new ResellListing(1L, "EXCHANGE", "1984", "George Orwell",
                "GOOD", null, "Brave New World", "Swap");

        when(users.findByEmailIgnoreCase("owner@example.com")).thenReturn(Optional.of(owner));
        when(users.findByEmailIgnoreCase("offerer@example.com")).thenReturn(Optional.of(offerer));
        when(users.findByEmailIgnoreCase("outsider@example.com")).thenReturn(Optional.of(outsider));
        when(listings.findById(10L)).thenReturn(Optional.of(exchangeListing));
        when(conversations.findByListingIdAndOffererId(10L, 2L)).thenReturn(Optional.empty());
        when(conversations.save(any(ExchangeConversation.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatService service = new ChatService(conversations, messages, listings, users);

        // Owner cannot start a chat with themselves
        assertThrows(ResponseStatusException.class, () -> service.openOrGetConversation("owner@example.com", 10L));

        // Offerer can open a 2-user conversation
        ExchangeConversation conv = service.openOrGetConversation("offerer@example.com", 10L);
        assertEquals(1L, conv.getOwnerId());
        assertEquals(2L, conv.getOffererId());

        // Authorized participants can send messages; third party is forbidden
        when(conversations.findById(99L)).thenReturn(Optional.of(conv));
        when(messages.save(any(ExchangeChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatService.ChatMessageView msg = service.sendMessage("offerer@example.com", 99L, "Can we swap tomorrow?");
        assertEquals("Can we swap tomorrow?", msg.content());
        assertEquals("Offerer", msg.senderName());

        assertThrows(ResponseStatusException.class,
                () -> service.sendMessage("outsider@example.com", 99L, "Let me join"));
    }
}
