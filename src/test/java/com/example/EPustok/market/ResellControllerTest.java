package com.example.EPustok.market;

import com.example.EPustok.identity.User;
import com.example.EPustok.identity.UserRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResellControllerTest {

    @Test
    void anyUserCanCreateResellAndExchangeListings() {
        ResellListings listings = mock(ResellListings.class);
        UserRepository users = mock(UserRepository.class);
        User buyer = new User("Buyer", "buyer@example.com", "hash");
        when(users.findByEmailIgnoreCase("buyer@example.com")).thenReturn(Optional.of(buyer));
        when(listings.save(any(ResellListing.class))).thenAnswer(inv -> inv.getArgument(0));

        ResellController controller = new ResellController(listings, users);
        var auth = new UsernamePasswordAuthenticationToken("buyer@example.com", "pw");

        String resellRedirect = controller.create(auth,
                new ResellController.ResellForm("RESELL", "Clean Code", "Robert Martin", "GOOD",
                        new BigDecimal("350.00"), null, "Hardcover"),
                new RedirectAttributesModelMap());
        assertEquals("redirect:/resell", resellRedirect);

        String exchangeRedirect = controller.create(auth,
                new ResellController.ResellForm("EXCHANGE", "The Hobbit", "J.R.R. Tolkien", "LIKE_NEW",
                        null, "The Silmarillion", "Negotiate via chat"),
                new RedirectAttributesModelMap());
        assertEquals("redirect:/resell", exchangeRedirect);
        assertNull(listings.findById(999L).orElse(null));
        verify(listings, org.mockito.Mockito.times(2)).save(any(ResellListing.class));
    }
}
