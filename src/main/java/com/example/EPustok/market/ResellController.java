package com.example.EPustok.market;

import com.example.EPustok.identity.User;
import com.example.EPustok.identity.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Lets any authenticated user list used books for resell or exchange, edit or remove their own listings, and make,
 * withdraw, accept or decline exchange offers. Rules are enforced in MarketService; this class maps results to pages.
 */
@Controller
@RequestMapping("/resell")
public class ResellController {
    private static final Set<String> ALLOWED_TYPES = Set.of("RESELL", "EXCHANGE");
    private static final Set<String> ALLOWED_CONDITIONS = Set.of("LIKE_NEW", "GOOD", "FAIR");

    private final ResellListings listings;
    private final UserRepository users;
    private final MarketService market;
    private final ExchangeOffers offers;

    public ResellController(ResellListings listings, UserRepository users, MarketService market, ExchangeOffers offers) {
        this.listings = listings;
        this.users = users;
        this.market = market;
        this.offers = offers;
    }

    public record ResellForm(
            @NotBlank String listingType,
            @NotBlank @Size(max = 255) String title,
            @NotBlank @Size(max = 255) String author,
            @NotBlank String bookCondition,
            @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
            @Size(max = 255) String desiredBook,
            @Size(max = 500) String description) {}

    public record ResellEditForm(
            @NotBlank String bookCondition,
            @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
            @Size(max = 500) String description) {}

    public record ExchangeOfferForm(
            @NotBlank @Size(max = 255) String offeredTitle,
            @NotBlank @Size(max = 255) String offeredAuthor) {}

    private User currentUser(Authentication authentication) {
        return users.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    @GetMapping
    public String myListings(Authentication authentication, Model model) {
        User user = currentUser(authentication);
        String email = authentication.getName();
        List<ResellListing> mine = listings.findByUserIdOrderByCreatedAtDesc(user.getId());

        // Every own listing gets a (possibly empty) entry so the template can index by listing id.
        Map<Long, List<ExchangeOffer>> offersByListing = new HashMap<>();
        List<Long> exchangeIds = new ArrayList<>();
        for (ResellListing listing : mine) {
            offersByListing.put(listing.getId(), new ArrayList<>());
            if ("EXCHANGE".equals(listing.getListingType())) {
                exchangeIds.add(listing.getId());
            }
        }
        if (!exchangeIds.isEmpty()) {
            for (ExchangeOffer offer : offers.findByListingIdInOrderByCreatedAtDesc(exchangeIds)) {
                offersByListing.get(offer.getListingId()).add(offer);
            }
        }

        List<ExchangeOffer> sentOffers = offers.findByOffererIdOrderByCreatedAtDesc(user.getId());
        Map<Long, String> offerListingTitles = new HashMap<>();
        List<Long> targetIds = sentOffers.stream().map(ExchangeOffer::getListingId).distinct().toList();
        if (!targetIds.isEmpty()) {
            for (ResellListing target : listings.findAllById(targetIds)) {
                offerListingTitles.put(target.getId(), target.getTitle());
            }
        }

        model.addAttribute("listings", mine);
        model.addAttribute("offersByListing", offersByListing);
        model.addAttribute("sentOffers", sentOffers);
        model.addAttribute("offerListingTitles", offerListingTitles);
        model.addAttribute("currentUserId", user.getId());
        model.addAttribute("displayName", email.contains("@") ? email.substring(0, email.indexOf('@')) : email);
        return "resell";
    }

    @PostMapping
    public String create(
            Authentication authentication,
            @Valid @ModelAttribute ResellForm form,
            BindingResult binding,
            RedirectAttributes flash) {
        if (binding.hasErrors()) {
            return failed(flash, "Check the listing: title and author are required (up to 255 characters), a resell listing needs a price above zero and notes are limited to 500 characters.");
        }
        try {
            String type = form.listingType().trim().toUpperCase();
            String condition = form.bookCondition().trim().toUpperCase();
            if (!ALLOWED_TYPES.contains(type) || !ALLOWED_CONDITIONS.contains(condition)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid listing type or condition");
            }
            if ("RESELL".equals(type) && form.price() == null) {
                return failed(flash, "Please enter a price for a resell listing.");
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
        } catch (ResponseStatusException e) {
            return failed(flash, reasonOf(e));
        }
        return "redirect:/resell";
    }

    @PostMapping("/{id}/edit")
    public String edit(
            Authentication authentication,
            @PathVariable long id,
            @Valid @ModelAttribute ResellEditForm form,
            BindingResult binding,
            RedirectAttributes flash) {
        if (binding.hasErrors()) {
            return failed(flash, "Check the listing: a condition is required, a resell price must be above zero and notes are limited to 500 characters.");
        }
        try {
            String condition = form.bookCondition().trim().toUpperCase();
            if (!ALLOWED_CONDITIONS.contains(condition)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid condition");
            }
            String desc = form.description() == null ? "" : form.description().trim();
            market.updateResell(authentication.getName(), id, condition, form.price(), desc);
            flash.addFlashAttribute("message", "Your listing has been updated.");
        } catch (ResponseStatusException e) {
            return failed(flash, reasonOf(e));
        }
        return "redirect:/resell";
    }

    @PostMapping("/{listingId}/offers")
    public String makeOffer(
            Authentication authentication,
            @PathVariable long listingId,
            @Valid @ModelAttribute ExchangeOfferForm form,
            BindingResult binding,
            RedirectAttributes flash) {
        if (binding.hasErrors()) {
            return failed(flash, "Enter the title and author of the book you are offering (up to 255 characters each).");
        }
        try {
            market.makeExchangeOffer(authentication.getName(), listingId, form.offeredTitle(), form.offeredAuthor());
            flash.addFlashAttribute("message", "Your offer has been sent to the listing owner.");
        } catch (ResponseStatusException e) {
            return failed(flash, reasonOf(e));
        }
        return "redirect:/resell";
    }

    @PostMapping("/{id}/close")
    public String close(Authentication authentication, @PathVariable long id, RedirectAttributes flash) {
        try {
            market.closeResell(authentication.getName(), id);
            flash.addFlashAttribute("message", "Listing closed.");
        } catch (ResponseStatusException e) {
            return failed(flash, reasonOf(e));
        }
        return "redirect:/resell";
    }

    @PostMapping("/{id}/delete")
    public String delete(Authentication authentication, @PathVariable long id, RedirectAttributes flash) {
        try {
            market.deleteResell(authentication.getName(), id);
            flash.addFlashAttribute("message", "Listing deleted.");
        } catch (ResponseStatusException e) {
            return failed(flash, reasonOf(e));
        }
        return "redirect:/resell";
    }

    /** Failures are flashed as both message and error so the page shows them as an alert. */
    private static String failed(RedirectAttributes flash, String reason) {
        flash.addFlashAttribute("message", reason);
        flash.addFlashAttribute("error", reason);
        return "redirect:/resell";
    }

    private static String reasonOf(ResponseStatusException e) {
        return e.getReason() != null ? e.getReason() : "The request could not be completed.";
    }
}
