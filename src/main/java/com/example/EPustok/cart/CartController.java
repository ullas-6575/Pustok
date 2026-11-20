package com.example.EPustok.cart;

import java.math.BigDecimal;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/buyer/cart")
public class CartController {
    private final CartService cart;

    public CartController(CartService cart) {
        this.cart = cart;
    }

    @GetMapping
    public String view(Authentication a, Model m) {
        var items = cart.items(a.getName());
        var resellItems = cart.resellItems(a.getName());
        BigDecimal booksTotal = items.stream()
                .map(CartService.Line::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal resellTotal = resellItems.stream()
                .map(CartService.ResellLine::price)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        m.addAttribute("items", items);
        m.addAttribute("resellItems", resellItems);
        m.addAttribute("totalCount", items.size() + resellItems.size());
        m.addAttribute("total", booksTotal.add(resellTotal));
        return "cart";
    }

    @PostMapping("/add")
    public String add(
            Authentication a,
            @RequestParam long bookId,
            @RequestParam(defaultValue = "1") int quantity,
            @RequestParam(required = false) String redirectTo,
            RedirectAttributes flash) {
        try {
            cart.add(a.getName(), bookId, quantity);
            flash.addFlashAttribute("message", "Book added to cart.");
        } catch (ResponseStatusException e) {
            flash.addFlashAttribute("cartError", e.getReason());
            flash.addFlashAttribute("message", e.getReason());
        }
        return "redirect:" + safeRedirect(redirectTo);
    }

    @PostMapping("/resell/add")
    public String addResell(
            Authentication a,
            @RequestParam long listingId,
            @RequestParam(required = false) String redirectTo,
            RedirectAttributes flash) {
        try {
            cart.addResell(a.getName(), listingId);
            flash.addFlashAttribute("message", "Used book added to cart.");
        } catch (ResponseStatusException e) {
            flash.addFlashAttribute("cartError", e.getReason());
            flash.addFlashAttribute("message", e.getReason());
        }
        return "redirect:" + safeRedirect(redirectTo);
    }

    @PostMapping("/{bookId}/update")
    public String update(Authentication a, @PathVariable long bookId, @RequestParam int quantity, RedirectAttributes flash) {
        try {
            cart.update(a.getName(), bookId, quantity);
        } catch (ResponseStatusException e) {
            flash.addFlashAttribute("message", e.getReason());
        }
        return "redirect:/buyer/cart";
    }

    @PostMapping("/{bookId}/remove")
    public String remove(Authentication a, @PathVariable long bookId) {
        cart.remove(a.getName(), bookId);
        return "redirect:/buyer/cart";
    }

    @PostMapping("/resell/{listingId}/remove")
    public String removeResell(Authentication a, @PathVariable long listingId) {
        cart.removeResell(a.getName(), listingId);
        return "redirect:/buyer/cart";
    }

    private String safeRedirect(String redirectTo) {
        if ("/catalog".equals(redirectTo)) {
            return "/catalog";
        }
        return "/buyer/cart";
    }
}
