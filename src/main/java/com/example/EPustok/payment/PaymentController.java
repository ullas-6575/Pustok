package com.example.EPustok.payment;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class PaymentController {
    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    /** The amount is never a request parameter: PaymentService reads the order total from the database. */
    @PostMapping("/buyer/orders/{id}/pay")
    public String pay(Authentication a, @PathVariable UUID id, @RequestParam String gateway,
                      @RequestParam String idempotencyKey, @RequestParam(required = false) String testCard,
                      RedirectAttributes flash) {
        try {
            flash.addFlashAttribute("message", messageFor(payments.pay(a.getName(), id, gateway, idempotencyKey, testCard)));
        } catch (ResponseStatusException e) {
            if (e.getReason() == null) {
                // 404 (not your order) and 401 carry no user-facing reason: keep their status.
                throw e;
            }
            flash.addFlashAttribute("message", e.getReason());
        } catch (IllegalArgumentException e) {
            // Unknown gateway keys and malformed idempotency keys: show them on the order page, as the form expects.
            flash.addFlashAttribute("message", e.getMessage());
        }
        return "redirect:/buyer/orders/" + id;
    }

    private static String messageFor(PaymentRecord p) {
        return switch (p.status()) {
            case COMPLETED -> "Payment completed. Your order is now processing.";
            case PENDING -> "Payment is pending. Your order status does not change until it is confirmed.";
            case FAILED -> "Payment was declined. You can try again with another method.";
        };
    }
}
