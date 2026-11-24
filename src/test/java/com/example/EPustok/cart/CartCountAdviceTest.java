package com.example.EPustok.cart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class CartCountAdviceTest {

    @Test
    void anonymousAndMissingAuthenticationGetZeroWithoutTouchingTheCart() {
        var cart = mock(CartService.class);
        var advice = new CartCountAdvice(cart);
        var anonymous = new AnonymousAuthenticationToken("key", "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));

        assertEquals(0, advice.cartCount(anonymous));
        assertEquals(0, advice.cartCount(null));
        verifyNoInteractions(cart);
    }

    @Test
    void adminHasNoCartSoGetsZeroWithoutTouchingTheCart() {
        var cart = mock(CartService.class);
        var admin = new UsernamePasswordAuthenticationToken("admin@example.com", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        assertEquals(0, new CartCountAdvice(cart).cartCount(admin));
        verifyNoInteractions(cart);
    }

    @Test
    void buyerGetsTheCartCount() {
        var cart = mock(CartService.class);
        when(cart.count("buyer@example.com")).thenReturn(3);
        var buyer = new UsernamePasswordAuthenticationToken("buyer@example.com", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_BUYER")));

        assertEquals(3, new CartCountAdvice(cart).cartCount(buyer));
    }

    @Test
    void failedCountFallsBackToZero() {
        var cart = mock(CartService.class);
        when(cart.count("seller@example.com")).thenThrow(new NoSuchElementException("no user"));
        var seller = new UsernamePasswordAuthenticationToken("seller@example.com", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_SELLER")));

        assertEquals(0, new CartCountAdvice(cart).cartCount(seller));
    }
}
