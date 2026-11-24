package com.example.EPustok.market;

import static com.example.EPustok.market.ExchangeState.ACCEPTED;
import static com.example.EPustok.market.ExchangeState.PROPOSED;
import static com.example.EPustok.market.ExchangeState.REJECTED;
import static com.example.EPustok.market.ExchangeState.WITHDRAWN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.EPustok.identity.User;
import com.example.EPustok.identity.UserRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.junit.jupiter.api.function.Executable;

/** Exchange offer lifecycle in MarketService: who may move an offer, which transitions are legal, and what is saved. */
class MarketExchangeOfferTest {

    private static final long OWNER = 2L, OFFERER = 3L, OTHER = 4L, LISTING = 5L, OFFER = 7L;

    private static void set(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static User user(long id, String email) {
        User u = new User("U", email, "hash");
        set(u, "id", id);
        return u;
    }

    private static ResellListing exchangeListing(String status) {
        ResellListing l = new ResellListing(OWNER, "EXCHANGE", "Clean Code", "Robert Martin", "GOOD",
                null, "Atomic Habits", "Hardcover");
        set(l, "id", LISTING);
        set(l, "status", status);
        return l;
    }

    private static ExchangeOffer offer(ExchangeState status) {
        ExchangeOffer o = new ExchangeOffer(LISTING, OFFERER, "Atomic Habits", "James Clear");
        set(o, "id", OFFER);
        set(o, "status", status);
        return o;
    }

    private record Fixture(ResellListings listings, ExchangeOffers offers, MarketService service) {}

    private static Fixture fixture(ResellListing listing, ExchangeOffer offer) {
        UserRepository users = mock(UserRepository.class);
        ResellListings listings = mock(ResellListings.class);
        ExchangeOffers offers = mock(ExchangeOffers.class);
        when(users.findByEmailIgnoreCase("owner@example.com")).thenReturn(Optional.of(user(OWNER, "owner@example.com")));
        when(users.findByEmailIgnoreCase("offerer@example.com")).thenReturn(Optional.of(user(OFFERER, "offerer@example.com")));
        when(users.findByEmailIgnoreCase("other@example.com")).thenReturn(Optional.of(user(OTHER, "other@example.com")));
        when(listings.findById(LISTING)).thenReturn(Optional.ofNullable(listing));
        when(listings.findByIdForUpdate(LISTING)).thenReturn(Optional.ofNullable(listing));
        when(offers.findById(OFFER)).thenReturn(Optional.ofNullable(offer));
        when(offers.saveAndFlush(any(ExchangeOffer.class))).thenAnswer(inv -> inv.getArgument(0));
        return new Fixture(listings, offers, new MarketService(users, mock(SellerProfiles.class), listings, offers));
    }

    private static void assertStatus(HttpStatus expected, Executable action) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, action);
        assertEquals(expected, ex.getStatusCode());
    }

    // ---- legal transitions: one test each ----

    @Test
    void ownerAcceptsProposedOffer() {
        Fixture f = fixture(exchangeListing("AVAILABLE"), offer(PROPOSED));
        when(f.listings().closeAvailable(LISTING, OWNER)).thenReturn(1);
        when(f.offers().transition(OFFER, PROPOSED, ACCEPTED)).thenReturn(1);
        f.service().acceptExchangeOffer("owner@example.com", OFFER);
        verify(f.offers()).transition(OFFER, PROPOSED, ACCEPTED);
    }

    @Test
    void ownerRejectsProposedOffer() {
        Fixture f = fixture(exchangeListing("AVAILABLE"), offer(PROPOSED));
        when(f.offers().transition(OFFER, PROPOSED, REJECTED)).thenReturn(1);
        f.service().rejectExchangeOffer("owner@example.com", OFFER);
        verify(f.offers()).transition(OFFER, PROPOSED, REJECTED);
    }

    @Test
    void offererWithdrawsProposedOffer() {
        Fixture f = fixture(exchangeListing("AVAILABLE"), offer(PROPOSED));
        when(f.offers().transition(OFFER, PROPOSED, WITHDRAWN)).thenReturn(1);
        f.service().withdrawExchangeOffer("offerer@example.com", OFFER);
        verify(f.offers()).transition(OFFER, PROPOSED, WITHDRAWN);
    }

    // ---- accept rule: one method, guarded updates in order ----

    @Test
    void acceptingRejectsCompetingOffersAndClosesListingInOneMethod() {
        Fixture f = fixture(exchangeListing("AVAILABLE"), offer(PROPOSED));
        when(f.listings().closeAvailable(LISTING, OWNER)).thenReturn(1);
        when(f.offers().transition(OFFER, PROPOSED, ACCEPTED)).thenReturn(1);
        f.service().acceptExchangeOffer("owner@example.com", OFFER);

        InOrder order = inOrder(f.listings(), f.offers());
        order.verify(f.listings()).closeAvailable(LISTING, OWNER);
        order.verify(f.offers()).transition(OFFER, PROPOSED, ACCEPTED);
        order.verify(f.offers()).rejectOtherProposed(LISTING, OFFER, PROPOSED, REJECTED);
    }

    @Test
    void secondAcceptOnSameListingIsConflictAndNothingIsWritten() {
        // The first accept already closed the listing, so the guarded close affects 0 rows.
        Fixture f = fixture(exchangeListing("CLOSED"), offer(PROPOSED));
        when(f.listings().closeAvailable(LISTING, OWNER)).thenReturn(0);
        assertStatus(HttpStatus.CONFLICT, () -> f.service().acceptExchangeOffer("owner@example.com", OFFER));
        verify(f.offers(), never()).transition(eq(OFFER), any(), any());
        verify(f.offers(), never()).rejectOtherProposed(eq(LISTING), eq(OFFER), any(), any());
    }

    @Test
    void offerDecidedUnderneathAcceptIsConflictAndCompetingOffersAreKept() {
        Fixture f = fixture(exchangeListing("AVAILABLE"), offer(PROPOSED));
        when(f.listings().closeAvailable(LISTING, OWNER)).thenReturn(1);
        when(f.offers().transition(OFFER, PROPOSED, ACCEPTED)).thenReturn(0);
        assertStatus(HttpStatus.CONFLICT, () -> f.service().acceptExchangeOffer("owner@example.com", OFFER));
        verify(f.offers(), never()).rejectOtherProposed(eq(LISTING), eq(OFFER), any(), any());
    }

    // ---- illegal transitions: 409 and nothing saved ----

    @Test
    void acceptingWithdrawnOfferIsConflictAndSavesNothing() {
        Fixture f = fixture(exchangeListing("AVAILABLE"), offer(WITHDRAWN));
        assertStatus(HttpStatus.CONFLICT, () -> f.service().acceptExchangeOffer("owner@example.com", OFFER));
        verify(f.listings(), never()).closeAvailable(eq(LISTING), eq(OWNER));
        verify(f.offers(), never()).transition(eq(OFFER), any(), any());
        verify(f.offers(), never()).rejectOtherProposed(eq(LISTING), eq(OFFER), any(), any());
    }

    @Test
    void rejectingAcceptedOfferIsConflictAndSavesNothing() {
        Fixture f = fixture(exchangeListing("CLOSED"), offer(ACCEPTED));
        assertStatus(HttpStatus.CONFLICT, () -> f.service().rejectExchangeOffer("owner@example.com", OFFER));
        verify(f.offers(), never()).transition(eq(OFFER), any(), any());
    }

    @Test
    void rejectingWithdrawnOfferIsConflictAndSavesNothing() {
        Fixture f = fixture(exchangeListing("AVAILABLE"), offer(WITHDRAWN));
        assertStatus(HttpStatus.CONFLICT, () -> f.service().rejectExchangeOffer("owner@example.com", OFFER));
        verify(f.offers(), never()).transition(eq(OFFER), any(), any());
    }

    @Test
    void withdrawingRejectedOfferIsConflictAndSavesNothing() {
        Fixture f = fixture(exchangeListing("AVAILABLE"), offer(REJECTED));
        assertStatus(HttpStatus.CONFLICT, () -> f.service().withdrawExchangeOffer("offerer@example.com", OFFER));
        verify(f.offers(), never()).transition(eq(OFFER), any(), any());
    }

    @Test
    void withdrawingAcceptedOfferIsConflictAndSavesNothing() {
        Fixture f = fixture(exchangeListing("CLOSED"), offer(ACCEPTED));
        assertStatus(HttpStatus.CONFLICT, () -> f.service().withdrawExchangeOffer("offerer@example.com", OFFER));
        verify(f.offers(), never()).transition(eq(OFFER), any(), any());
    }

    // ---- who may act: 403 before any state check ----

    @Test
    void withdrawByNonOffererIsForbiddenAndSavesNothing() {
        Fixture f = fixture(exchangeListing("AVAILABLE"), offer(PROPOSED));
        assertStatus(HttpStatus.FORBIDDEN, () -> f.service().withdrawExchangeOffer("other@example.com", OFFER));
        assertStatus(HttpStatus.FORBIDDEN, () -> f.service().withdrawExchangeOffer("owner@example.com", OFFER));
        verify(f.offers(), never()).transition(eq(OFFER), any(), any());
    }

    @Test
    void acceptByNonOwnerIsForbiddenAndSavesNothing() {
        Fixture f = fixture(exchangeListing("AVAILABLE"), offer(PROPOSED));
        assertStatus(HttpStatus.FORBIDDEN, () -> f.service().acceptExchangeOffer("offerer@example.com", OFFER));
        assertStatus(HttpStatus.FORBIDDEN, () -> f.service().acceptExchangeOffer("other@example.com", OFFER));
        verify(f.listings(), never()).closeAvailable(eq(LISTING), eq(OWNER));
        verify(f.offers(), never()).transition(eq(OFFER), any(), any());
    }

    @Test
    void rejectByOffererIsForbiddenAndSavesNothing() {
        Fixture f = fixture(exchangeListing("AVAILABLE"), offer(PROPOSED));
        assertStatus(HttpStatus.FORBIDDEN, () -> f.service().rejectExchangeOffer("offerer@example.com", OFFER));
        verify(f.offers(), never()).transition(eq(OFFER), any(), any());
    }

    @Test
    void unknownOfferIsNotFound() {
        Fixture f = fixture(exchangeListing("AVAILABLE"), null);
        assertStatus(HttpStatus.NOT_FOUND, () -> f.service().acceptExchangeOffer("owner@example.com", OFFER));
        assertStatus(HttpStatus.NOT_FOUND, () -> f.service().rejectExchangeOffer("owner@example.com", OFFER));
        assertStatus(HttpStatus.NOT_FOUND, () -> f.service().withdrawExchangeOffer("offerer@example.com", OFFER));
    }

    // ---- making an offer ----

    @Test
    void validOfferIsSavedAsProposedWithTrimmedText() {
        Fixture f = fixture(exchangeListing("AVAILABLE"), null);
        ExchangeOffer saved = f.service().makeExchangeOffer("offerer@example.com", LISTING, "  The Hobbit ", " J.R.R. Tolkien ");
        assertEquals(PROPOSED, saved.getStatus());
        assertEquals("The Hobbit", saved.getOfferedTitle());
        assertEquals("J.R.R. Tolkien", saved.getOfferedAuthor());
        assertEquals(OFFERER, saved.getOffererId());
    }

    @Test
    void offerOnOwnListingIsBadRequestAndSavesNothing() {
        Fixture f = fixture(exchangeListing("AVAILABLE"), null);
        assertStatus(HttpStatus.BAD_REQUEST, () -> f.service().makeExchangeOffer("owner@example.com", LISTING, "T", "A"));
        verify(f.offers(), never()).saveAndFlush(any(ExchangeOffer.class));
    }

    @Test
    void offerOnClosedListingIsConflictAndSavesNothing() {
        Fixture f = fixture(exchangeListing("CLOSED"), null);
        assertStatus(HttpStatus.CONFLICT, () -> f.service().makeExchangeOffer("offerer@example.com", LISTING, "T", "A"));
        verify(f.offers(), never()).saveAndFlush(any(ExchangeOffer.class));
    }

    @Test
    void offerOnResellListingIsBadRequestAndSavesNothing() {
        ResellListing resell = new ResellListing(OWNER, "RESELL", "Clean Code", "Robert Martin", "GOOD",
                new BigDecimal("350.00"), null, "");
        set(resell, "id", LISTING);
        Fixture f = fixture(resell, null);
        assertStatus(HttpStatus.BAD_REQUEST, () -> f.service().makeExchangeOffer("offerer@example.com", LISTING, "T", "A"));
        verify(f.offers(), never()).saveAndFlush(any(ExchangeOffer.class));
    }

    @Test
    void secondProposedOfferBySameUserIsConflictAndSavesNothing() {
        Fixture f = fixture(exchangeListing("AVAILABLE"), null);
        when(f.offers().existsByListingIdAndOffererIdAndStatus(LISTING, OFFERER, PROPOSED)).thenReturn(true);
        assertStatus(HttpStatus.CONFLICT, () -> f.service().makeExchangeOffer("offerer@example.com", LISTING, "T", "A"));
        verify(f.offers(), never()).saveAndFlush(any(ExchangeOffer.class));
    }

    @Test
    void duplicateOfferCaughtByPartialUniqueIndexIsConflict() {
        Fixture f = fixture(exchangeListing("AVAILABLE"), null);
        when(f.offers().saveAndFlush(any(ExchangeOffer.class))).thenThrow(new DataIntegrityViolationException("uq_exchange_offer_one_proposed"));
        assertStatus(HttpStatus.CONFLICT, () -> f.service().makeExchangeOffer("offerer@example.com", LISTING, "T", "A"));
    }

    @Test
    void blankOfferedTitleOrAuthorIsBadRequestAndSavesNothing() {
        Fixture f = fixture(exchangeListing("AVAILABLE"), null);
        assertStatus(HttpStatus.BAD_REQUEST, () -> f.service().makeExchangeOffer("offerer@example.com", LISTING, "   ", "A"));
        assertStatus(HttpStatus.BAD_REQUEST, () -> f.service().makeExchangeOffer("offerer@example.com", LISTING, "T", null));
        verify(f.offers(), never()).saveAndFlush(any(ExchangeOffer.class));
    }

    @Test
    void overlongOfferedTitleIsBadRequestAndSavesNothing() {
        Fixture f = fixture(exchangeListing("AVAILABLE"), null);
        assertStatus(HttpStatus.BAD_REQUEST, () -> f.service().makeExchangeOffer("offerer@example.com", LISTING, "x".repeat(256), "A"));
        verify(f.offers(), never()).saveAndFlush(any(ExchangeOffer.class));
    }

    // ---- closing a listing: its live offers are rejected in the same call ----

    @Test
    void closingExchangeListingRejectsItsProposedOffersAfterClosing() {
        Fixture f = fixture(exchangeListing("AVAILABLE"), offer(PROPOSED));
        when(f.listings().closeAvailable(LISTING, OWNER)).thenReturn(1);
        f.service().closeResell("owner@example.com", LISTING);

        InOrder order = inOrder(f.listings(), f.offers());
        order.verify(f.listings()).closeAvailable(LISTING, OWNER);
        order.verify(f.offers()).rejectAllProposed(LISTING, PROPOSED, REJECTED);
    }

    @Test
    void closingResellListingLeavesOffersAlone() {
        ResellListing resell = new ResellListing(OWNER, "RESELL", "Clean Code", "Robert Martin", "GOOD",
                new BigDecimal("350.00"), null, "");
        set(resell, "id", LISTING);
        Fixture f = fixture(resell, null);
        when(f.listings().closeAvailable(LISTING, OWNER)).thenReturn(1);
        f.service().closeResell("owner@example.com", LISTING);
        verify(f.offers(), never()).rejectAllProposed(anyLong(), any(), any());
    }

    @Test
    void closingAlreadyClosedExchangeListingWritesNoOffers() {
        Fixture f = fixture(exchangeListing("CLOSED"), offer(PROPOSED));
        when(f.listings().closeAvailable(LISTING, OWNER)).thenReturn(0);
        assertStatus(HttpStatus.CONFLICT, () -> f.service().closeResell("owner@example.com", LISTING));
        verify(f.offers(), never()).rejectAllProposed(anyLong(), any(), any());
    }

    // ---- row lock: an offer serializes with accept and close on the same listing ----

    @Test
    void makingOfferLocksListingRowBeforeCheckingItsState() {
        Fixture f = fixture(exchangeListing("AVAILABLE"), null);
        f.service().makeExchangeOffer("offerer@example.com", LISTING, "The Hobbit", "J.R.R. Tolkien");
        verify(f.listings()).findByIdForUpdate(LISTING);
        verify(f.listings(), never()).findById(LISTING);
    }
}
