package com.example.EPustok.chat;

import com.example.EPustok.identity.User;
import com.example.EPustok.identity.UserRepository;
import com.example.EPustok.market.ResellListing;
import com.example.EPustok.market.ResellListings;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    private ResellListing exchangeListing() {
        return new ResellListing(1L, "EXCHANGE", "1984", "George Orwell",
                "GOOD", null, "Brave New World", "Swap");
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

        ResellListing exchangeListing = exchangeListing();

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

    @Test
    void participantCheckIsTrueOnlyForConversationMembers() throws Exception {
        ExchangeConversations conversations = mock(ExchangeConversations.class);
        UserRepository users = mock(UserRepository.class);

        User owner = userWithId(1L, "Owner", "owner@example.com");
        User offerer = userWithId(2L, "Offerer", "offerer@example.com");
        User outsider = userWithId(3L, "Outsider", "outsider@example.com");
        when(users.findByEmailIgnoreCase("owner@example.com")).thenReturn(Optional.of(owner));
        when(users.findByEmailIgnoreCase("offerer@example.com")).thenReturn(Optional.of(offerer));
        when(users.findByEmailIgnoreCase("outsider@example.com")).thenReturn(Optional.of(outsider));
        when(users.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());
        when(conversations.findById(99L)).thenReturn(Optional.of(new ExchangeConversation(10L, 1L, 2L)));

        ChatService service = new ChatService(conversations, mock(ExchangeChatMessages.class), mock(ResellListings.class), users);

        assertTrue(service.isParticipant("owner@example.com", 99L));
        assertTrue(service.isParticipant("offerer@example.com", 99L));
        assertFalse(service.isParticipant("outsider@example.com", 99L));
        assertFalse(service.isParticipant("nobody@example.com", 99L));
    }

    @Test
    void sendRateLimitAllowsFiveMessagesPerWindowAndRecovers() {
        ChatService service = new ChatService(
                mock(ExchangeConversations.class),
                mock(ExchangeChatMessages.class),
                mock(ResellListings.class),
                mock(UserRepository.class));

        for (int i = 0; i < 5; i++) {
            service.reserveSendSlot(7L, 1_000L + i);
        }

        // The sixth send inside the 10 second window is refused with 429
        ResponseStatusException blocked =
                assertThrows(ResponseStatusException.class, () -> service.reserveSendSlot(7L, 1_005L));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, blocked.getStatusCode());

        // The limit is per user
        assertDoesNotThrow(() -> service.reserveSendSlot(8L, 1_005L));

        // Once the oldest send is 10 seconds old, the user may send again
        assertDoesNotThrow(() -> service.reserveSendSlot(7L, 11_000L));
    }

    @Test
    void openOrGetConversationReturnsTheRowCreatedByAConcurrentRequest() throws Exception {
        ExchangeConversations conversations = mock(ExchangeConversations.class);
        ResellListings listings = mock(ResellListings.class);
        UserRepository users = mock(UserRepository.class);

        User offerer = userWithId(2L, "Offerer", "offerer@example.com");
        ExchangeConversation winner = new ExchangeConversation(10L, 1L, 2L);

        when(users.findByEmailIgnoreCase("offerer@example.com")).thenReturn(Optional.of(offerer));
        when(listings.findById(10L)).thenReturn(Optional.of(exchangeListing()));
        // First lookup finds nothing, the insert loses the unique-constraint race, the re-read finds the winner's row
        when(conversations.findByListingIdAndOffererId(10L, 2L)).thenReturn(Optional.empty(), Optional.of(winner));
        when(conversations.save(any(ExchangeConversation.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        ChatService service = new ChatService(conversations, mock(ExchangeChatMessages.class), listings, users);

        assertSame(winner, service.openOrGetConversation("offerer@example.com", 10L));
    }
}
