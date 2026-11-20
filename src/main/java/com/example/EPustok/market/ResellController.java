package com.example.EPustok.market;

import com.example.EPustok.identity.User;
import com.example.EPustok.identity.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Allows any authenticated user to list used books for resell or exchange.
 */
@Controller
@RequestMapping("/resell")
public class ResellController {
    private static final Set<String> ALLOWED_TYPES = Set.of("RESELL", "EXCHANGE");
    private static final Set<String> ALLOWED_CONDITIONS = Set.of("LIKE_NEW", "GOOD", "FAIR");

    private final ResellListings listings;
    private final UserRepository users;

    public ResellController(ResellListings listings, UserRepository users) {
        this.listings = listings;
        this.users = users;
    }

    public record ResellForm(
            @NotBlank String listingType,
            @NotBlank @Size(max = 255) String title,
            @NotBlank @Size(max = 255) String author,
            @NotBlank String bookCondition,
            @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
            @Size(max = 255) String desiredBook,
            @Size(max = 500) String description) {}

    private User currentUser(Authentication authentication) {
        return users.findByEmailIgnoreCase(authentication.getName()).orElseThrow();
    }

    @GetMapping
    public String myListings(Authentication authentication, Model model) {
        User user = currentUser(authentication);
        String email = authentication.getName();
        model.addAttribute("listings", listings.findByUserIdOrderByCreatedAtDesc(user.getId()));
        model.addAttribute("displayName", email.contains("@") ? email.substring(0, email.indexOf('@')) : email);
        return "resell";
    }

    @PostMapping
    public String create(
            Authentication authentication,
            @Valid @ModelAttribute ResellForm form,
            RedirectAttributes flash) {
        String type = form.listingType().trim().toUpperCase();
        String condition = form.bookCondition().trim().toUpperCase();
        if (!ALLOWED_TYPES.contains(type) || !ALLOWED_CONDITIONS.contains(condition)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid listing type or condition");
        }
        if ("RESELL".equals(type) && form.price() == null) {
            flash.addFlashAttribute("error", "Please enter a price for a resell listing.");
            return "redirect:/resell";
        }

        User user = currentUser(authentication);
        BigDecimal price = "RESELL".equals(type) ? form.price() : null;
        String desired = "EXCHANGE".equals(type)
                ? (form.desiredBook() == null || form.desiredBook().isBlank() ? "Open to book exchange offers" : form.desiredBook().trim())
                : null;
        String desc = form.description() == null ? "" : form.description().trim();

        listings.save(new ResellListing(
                user.getId(),
                type,
                form.title().trim(),
                form.author().trim(),
                condition,
                price,
                desired,
                desc));

        flash.addFlashAttribute("message", "RESELL".equals(type)
                ? "Your used book is now listed in the Resell catalog."
                : "Your book is now listed for Exchange.");
        return "redirect:/resell";
    }

    @PostMapping("/{id}/close")
    public String close(Authentication authentication, @PathVariable long id) {
        ResellListing listing = ownedOrAdmin(id, currentUser(authentication));
        listing.close();
        listings.save(listing);
        return "redirect:/resell";
    }

    @PostMapping("/{id}/delete")
    public String delete(Authentication authentication, @PathVariable long id) {
        ResellListing listing = ownedOrAdmin(id, currentUser(authentication));
        listings.delete(listing);
        return "redirect:/resell";
    }

    private ResellListing ownedOrAdmin(long id, User user) {
        ResellListing listing = listings.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!listing.getUserId().equals(user.getId()) && !"ADMIN".equals(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return listing;
    }
}
