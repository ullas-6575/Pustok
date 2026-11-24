package com.example.EPustok.cart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Supplies the cart count shown in the top navigation on every page. Only buyers and sellers have a cart;
 * anonymous users and other roles get 0. A failed count is logged and shown as 0 so the page still renders.
 */
@ControllerAdvice
public class CartCountAdvice {
    private static final Logger log = LoggerFactory.getLogger(CartCountAdvice.class);

    private final CartService cart;

    public CartCountAdvice(CartService cart) {
        this.cart = cart;
    }

    @ModelAttribute("cartCount")
    public int cartCount(Authentication authentication) {
        if (!canUseCart(authentication)) {
            return 0;
        }
        try {
            return cart.count(authentication.getName());
        } catch (RuntimeException ex) {
            log.warn("Could not load the cart count for the navigation bar", ex);
            return 0;
        }
    }

    static boolean canUseCart(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_BUYER".equals(a.getAuthority()) || "ROLE_SELLER".equals(a.getAuthority()));
    }
}
