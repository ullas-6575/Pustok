package com.example.EPustok.market;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Owner accepts or rejects an exchange offer; the offerer withdraws their own. Rules live in MarketService. */
@Controller
@RequestMapping("/exchange-offers")
public class ExchangeOfferController {
    private final MarketService market;

    public ExchangeOfferController(MarketService market) {
        this.market = market;
    }

    @PostMapping("/{offerId}/accept")
    public String accept(Authentication authentication, @PathVariable long offerId, RedirectAttributes flash) {
        try {
            market.acceptExchangeOffer(authentication.getName(), offerId);
            flash.addFlashAttribute("message", "Offer accepted. The other offers on this listing were declined.");
        } catch (ResponseStatusException e) {
            return failed(flash, e);
        }
        return "redirect:/resell";
    }

    @PostMapping("/{offerId}/reject")
    public String reject(Authentication authentication, @PathVariable long offerId, RedirectAttributes flash) {
        try {
            market.rejectExchangeOffer(authentication.getName(), offerId);
            flash.addFlashAttribute("message", "Offer declined.");
        } catch (ResponseStatusException e) {
            return failed(flash, e);
        }
        return "redirect:/resell";
    }

    @PostMapping("/{offerId}/withdraw")
    public String withdraw(Authentication authentication, @PathVariable long offerId, RedirectAttributes flash) {
        try {
            market.withdrawExchangeOffer(authentication.getName(), offerId);
            flash.addFlashAttribute("message", "Your offer has been withdrawn.");
        } catch (ResponseStatusException e) {
            return failed(flash, e);
        }
        return "redirect:/resell";
    }

    /** Failures are flashed as both message and error so the resell page shows them as an alert. */
    private static String failed(RedirectAttributes flash, ResponseStatusException e) {
        String reason = e.getReason() != null ? e.getReason() : "The request could not be completed.";
        flash.addFlashAttribute("message", reason);
        flash.addFlashAttribute("error", reason);
        return "redirect:/resell";
    }
}
