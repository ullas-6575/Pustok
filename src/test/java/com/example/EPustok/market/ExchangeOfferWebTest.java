package com.example.EPustok.market;

import static com.example.EPustok.market.ExchangeState.ACCEPTED;
import static com.example.EPustok.market.ExchangeState.PROPOSED;
import static com.example.EPustok.market.ExchangeState.WITHDRAWN;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.example.EPustok.identity.User;
import com.example.EPustok.identity.UserRepository;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Exchange offer HTML routes: illegal or forbidden actions redirect back to /resell with the reason flashed as an
 * error, so no raw error page is shown. Unknown offers and bad input use the same path.
 */
class ExchangeOfferWebTest {

    private static final long OWNER = 2L, OFFERER = 3L, OTHER = 4L, LISTING = 5L, OFFER = 7L;
    private static final UsernamePasswordAuthenticationToken OWNER_AUTH =
            new UsernamePasswordAuthenticationToken("owner@example.com", "pw");
    private static final UsernamePasswordAuthenticationToken OFFERER_AUTH =
            new UsernamePasswordAuthenticationToken("offerer@example.com", "pw");
    private static final UsernamePasswordAuthenticationToken OTHER_AUTH =
            new UsernamePasswordAuthenticationToken("other@example.com", "pw");

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

    private record Setup(ResellListings listings, ExchangeOffers offers, MockMvc mvc) {}

    private static Setup setup(ExchangeState offerStatus, String listingStatus) {
        UserRepository users = mock(UserRepository.class);
        ResellListings listings = mock(ResellListings.class);
        ExchangeOffers offers = mock(ExchangeOffers.class);
        when(users.findByEmailIgnoreCase("owner@example.com")).thenReturn(Optional.of(user(OWNER, "owner@example.com")));
        when(users.findByEmailIgnoreCase("offerer@example.com")).thenReturn(Optional.of(user(OFFERER, "offerer@example.com")));
        when(users.findByEmailIgnoreCase("other@example.com")).thenReturn(Optional.of(user(OTHER, "other@example.com")));

        ResellListing listing = new ResellListing(OWNER, "EXCHANGE", "Clean Code", "Robert Martin", "GOOD", null, "Atomic Habits", "");
        set(listing, "id", LISTING);
        set(listing, "status", listingStatus);
        when(listings.findById(LISTING)).thenReturn(Optional.of(listing));
        when(listings.findByIdForUpdate(LISTING)).thenReturn(Optional.of(listing));

        if (offerStatus != null) {
            ExchangeOffer offer = new ExchangeOffer(LISTING, OFFERER, "Atomic Habits", "James Clear");
            set(offer, "id", OFFER);
            set(offer, "status", offerStatus);
            when(offers.findById(OFFER)).thenReturn(Optional.of(offer));
        }
        when(offers.saveAndFlush(any(ExchangeOffer.class))).thenAnswer(inv -> inv.getArgument(0));

        MarketService market = new MarketService(users, mock(SellerProfiles.class), listings, offers);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new ExchangeOfferController(market), new ResellController(listings, users, market, offers)).build();
        return new Setup(listings, offers, mvc);
    }

    @Test
    void ownerAcceptRedirectsAndClosesListing() throws Exception {
        Setup s = setup(PROPOSED, "AVAILABLE");
        when(s.listings().closeAvailable(LISTING, OWNER)).thenReturn(1);
        when(s.offers().transition(OFFER, PROPOSED, ACCEPTED)).thenReturn(1);
        s.mvc().perform(post("/exchange-offers/7/accept").principal(OWNER_AUTH))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/resell"))
                .andExpect(flash().attributeExists("message"));
    }

    @Test
    void acceptingWithdrawnOfferRedirectsWithErrorAndWritesNothing() throws Exception {
        Setup s = setup(WITHDRAWN, "AVAILABLE");
        s.mvc().perform(post("/exchange-offers/7/accept").principal(OWNER_AUTH))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/resell"))
                .andExpect(flash().attributeExists("error"));
        verify(s.listings(), never()).closeAvailable(eq(LISTING), eq(OWNER));
    }

    @Test
    void secondAcceptOnClosedListingRedirectsWithError() throws Exception {
        Setup s = setup(PROPOSED, "CLOSED");
        when(s.listings().closeAvailable(LISTING, OWNER)).thenReturn(0);
        s.mvc().perform(post("/exchange-offers/7/accept").principal(OWNER_AUTH))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("error"));
        verify(s.offers(), never()).transition(eq(OFFER), any(), any());
    }

    @Test
    void rejectingAcceptedOfferRedirectsWithError() throws Exception {
        Setup s = setup(ACCEPTED, "CLOSED");
        s.mvc().perform(post("/exchange-offers/7/reject").principal(OWNER_AUTH))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("error"));
        verify(s.offers(), never()).transition(eq(OFFER), any(), any());
    }

    @Test
    void withdrawByNonOffererRedirectsWithError() throws Exception {
        Setup s = setup(PROPOSED, "AVAILABLE");
        s.mvc().perform(post("/exchange-offers/7/withdraw").principal(OTHER_AUTH))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("error"));
        verify(s.offers(), never()).transition(eq(OFFER), any(), any());
    }

    @Test
    void offererWithdrawRedirects() throws Exception {
        Setup s = setup(PROPOSED, "AVAILABLE");
        when(s.offers().transition(OFFER, PROPOSED, WITHDRAWN)).thenReturn(1);
        s.mvc().perform(post("/exchange-offers/7/withdraw").principal(OFFERER_AUTH))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/resell"));
    }

    @Test
    void unknownOfferRedirectsWithError() throws Exception {
        Setup s = setup(null, "AVAILABLE");
        s.mvc().perform(post("/exchange-offers/7/reject").principal(OWNER_AUTH))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("error"));
    }

    @Test
    void blankOfferedTitleRedirectsWithErrorWithoutSaving() throws Exception {
        Setup s = setup(null, "AVAILABLE");
        s.mvc().perform(post("/resell/5/offers").principal(OFFERER_AUTH)
                        .param("offeredTitle", "   ").param("offeredAuthor", "James Clear"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("error"));
        verify(s.offers(), never()).saveAndFlush(any(ExchangeOffer.class));
    }

    @Test
    void blankOfferedAuthorRedirectsWithErrorWithoutSaving() throws Exception {
        Setup s = setup(null, "AVAILABLE");
        s.mvc().perform(post("/resell/5/offers").principal(OFFERER_AUTH)
                        .param("offeredTitle", "Atomic Habits").param("offeredAuthor", ""))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("error"));
        verify(s.offers(), never()).saveAndFlush(any(ExchangeOffer.class));
    }

    @Test
    void validOfferRedirectsToResell() throws Exception {
        Setup s = setup(null, "AVAILABLE");
        s.mvc().perform(post("/resell/5/offers").principal(OFFERER_AUTH)
                        .param("offeredTitle", "Atomic Habits").param("offeredAuthor", "James Clear"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/resell"));
        verify(s.offers()).saveAndFlush(any(ExchangeOffer.class));
    }
}
