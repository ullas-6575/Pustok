package com.example.EPustok.market;

import com.example.EPustok.identity.User;
import com.example.EPustok.identity.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ResellControllerTest {

    private static User signedInUser(long id, String email) {
        User user = new User("Buyer", email, "hash");
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return user;
    }

    private record Setup(ResellListings listings, UserRepository users, ResellController controller, MockMvc mvc) {}

    private static Setup setup() {
        ResellListings listings = mock(ResellListings.class);
        UserRepository users = mock(UserRepository.class);
        User buyer = signedInUser(2L, "buyer@example.com");
        when(users.findByEmailIgnoreCase("buyer@example.com")).thenReturn(Optional.of(buyer));
        when(listings.save(any(ResellListing.class))).thenAnswer(inv -> inv.getArgument(0));
        MarketService market = new MarketService(users, mock(SellerProfiles.class), listings, mock(ExchangeOffers.class));
        ResellController controller = new ResellController(listings, users, market, mock(ExchangeOffers.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        return new Setup(listings, users, controller, mvc);
    }

    private static final UsernamePasswordAuthenticationToken BUYER = new UsernamePasswordAuthenticationToken("buyer@example.com", "pw");

    @Test
    void anyUserCanCreateResellAndExchangeListings() {
        ResellListings listings = mock(ResellListings.class);
        UserRepository users = mock(UserRepository.class);
        User buyer = new User("Buyer", "buyer@example.com", "hash");
        when(users.findByEmailIgnoreCase("buyer@example.com")).thenReturn(Optional.of(buyer));
        when(listings.save(any(ResellListing.class))).thenAnswer(inv -> inv.getArgument(0));

        ResellController controller = new ResellController(listings, users, mock(MarketService.class), mock(ExchangeOffers.class));
        var auth = new UsernamePasswordAuthenticationToken("buyer@example.com", "pw");

        var resellForm = new ResellController.ResellForm("RESELL", "Clean Code", "Robert Martin", "GOOD",
                new BigDecimal("350.00"), null, "Hardcover");
        String resellRedirect = controller.create(auth, resellForm,
                new BeanPropertyBindingResult(resellForm, "resellForm"), new RedirectAttributesModelMap());
        assertEquals("redirect:/resell", resellRedirect);

        var exchangeForm = new ResellController.ResellForm("EXCHANGE", "The Hobbit", "J.R.R. Tolkien", "LIKE_NEW",
                null, "The Silmarillion", "Negotiate via chat");
        String exchangeRedirect = controller.create(auth, exchangeForm,
                new BeanPropertyBindingResult(exchangeForm, "resellForm"), new RedirectAttributesModelMap());
        assertEquals("redirect:/resell", exchangeRedirect);
        assertNull(listings.findById(999L).orElse(null));
        verify(listings, org.mockito.Mockito.times(2)).save(any(ResellListing.class));
    }

    @Test
    void createIgnoresClientSuppliedStatusAndOwner() throws Exception {
        Setup s = setup();
        s.mvc().perform(post("/resell").principal(BUYER)
                        .param("listingType", "RESELL").param("title", "Clean Code").param("author", "Robert Martin")
                        .param("bookCondition", "GOOD").param("price", "350.00")
                        .param("status", "CLOSED").param("userId", "99"))
                .andExpect(status().is3xxRedirection());
        ArgumentCaptor<ResellListing> saved = ArgumentCaptor.forClass(ResellListing.class);
        verify(s.listings()).save(saved.capture());
        assertEquals("AVAILABLE", saved.getValue().getStatus());
        assertEquals(2L, saved.getValue().getUserId());
    }

    @Test
    void createRejectsBlankTitleWithoutSavingAndFlashesTheReason() throws Exception {
        Setup s = setup();
        s.mvc().perform(post("/resell").principal(BUYER)
                        .param("listingType", "RESELL").param("title", "   ").param("author", "Robert Martin")
                        .param("bookCondition", "GOOD").param("price", "350.00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
        verify(s.listings(), never()).save(any(ResellListing.class));
    }

    @Test
    void createRejectsZeroPriceWithoutSaving() throws Exception {
        Setup s = setup();
        s.mvc().perform(post("/resell").principal(BUYER)
                        .param("listingType", "RESELL").param("title", "Clean Code").param("author", "Robert Martin")
                        .param("bookCondition", "GOOD").param("price", "0"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
        verify(s.listings(), never()).save(any(ResellListing.class));
    }

    @Test
    void createRejectsNegativePriceWithoutSaving() throws Exception {
        Setup s = setup();
        s.mvc().perform(post("/resell").principal(BUYER)
                        .param("listingType", "RESELL").param("title", "Clean Code").param("author", "Robert Martin")
                        .param("bookCondition", "GOOD").param("price", "-1.00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
        verify(s.listings(), never()).save(any(ResellListing.class));
    }

    @Test
    void createRejectsOverlongNotesWithoutSaving() throws Exception {
        Setup s = setup();
        s.mvc().perform(post("/resell").principal(BUYER)
                        .param("listingType", "RESELL").param("title", "Clean Code").param("author", "Robert Martin")
                        .param("bookCondition", "GOOD").param("price", "350.00").param("description", "x".repeat(501)))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
        verify(s.listings(), never()).save(any(ResellListing.class));
    }

    @Test
    void editRejectsZeroPriceWithoutSaving() throws Exception {
        Setup s = setup();
        s.mvc().perform(post("/resell/5/edit").principal(BUYER)
                        .param("bookCondition", "GOOD").param("price", "0"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
        verify(s.listings(), never()).updateAvailable(ArgumentMatchers.anyLong(), ArgumentMatchers.anyLong(),
                any(), any(), any());
    }

    @Test
    void signedInUserSeesOnlyOwnListings() {
        ResellListings listings = mock(ResellListings.class);
        UserRepository users = mock(UserRepository.class);
        User buyer = signedInUser(2L, "buyer@example.com");
        when(users.findByEmailIgnoreCase("buyer@example.com")).thenReturn(Optional.of(buyer));
        ResellListing mine = new ResellListing(2L, "RESELL", "Clean Code", "Robert Martin", "GOOD", new BigDecimal("350.00"), null, "");
        when(listings.findByUserIdOrderByCreatedAtDesc(2L)).thenReturn(List.of(mine));

        ExtendedModelMap model = new ExtendedModelMap();
        new ResellController(listings, users, mock(MarketService.class), mock(ExchangeOffers.class)).myListings(BUYER, model);
        assertEquals(List.of(mine), model.get("listings"));
        verify(listings, never()).findAll();
    }

    @Test
    void ownExchangeListingShowsIncomingOffersAndOwnerActions() throws Exception {
        ResellListings listings = mock(ResellListings.class);
        UserRepository users = mock(UserRepository.class);
        ExchangeOffers offers = mock(ExchangeOffers.class);
        User owner = signedInUser(2L, "buyer@example.com");
        when(users.findByEmailIgnoreCase("buyer@example.com")).thenReturn(Optional.of(owner));
        ResellListing exchange = new ResellListing(2L, "EXCHANGE", "Clean Code", "Robert Martin", "GOOD", null, "Atomic Habits", "");
        var idField = ResellListing.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(exchange, 5L);
        when(listings.findByUserIdOrderByCreatedAtDesc(2L)).thenReturn(List.of(exchange));
        ExchangeOffer offer = new ExchangeOffer(5L, 3L, "Dune", "Frank Herbert");
        when(offers.findByListingIdInOrderByCreatedAtDesc(any())).thenReturn(List.of(offer));

        ExtendedModelMap model = new ExtendedModelMap();
        new ResellController(listings, users, mock(MarketService.class), offers).myListings(BUYER, model);

        @SuppressWarnings("unchecked")
        Map<Long, List<ExchangeOffer>> byListing = (Map<Long, List<ExchangeOffer>>) model.get("offersByListing");
        assertEquals(List.of(offer), byListing.get(5L));
        assertEquals(2L, model.get("currentUserId"));
    }
}
