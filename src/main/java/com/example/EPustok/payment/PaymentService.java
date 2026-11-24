package com.example.EPustok.payment;

import com.example.EPustok.identity.UserRepository;
import com.example.EPustok.payment.PaymentStore.BuyerOrder;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Charges an order through a PaymentGateway. The amount is always the order total from the database.
 * Idempotency: a replayed key returns the stored result without a second row or a second charge. The
 * order row lock serializes requests for one order, and the UNIQUE constraints in V12 are the backstop.
 */
@Service
@Transactional
public class PaymentService {
    static final int MAX_KEY_LENGTH = 100;

    private final PaymentStore store;
    private final PaymentGatewayFactory gateways;
    private final UserRepository users;

    public PaymentService(PaymentStore store, PaymentGatewayFactory gateways, UserRepository users) {
        this.store = store;
        this.gateways = gateways;
        this.users = users;
    }

    @PreAuthorize("hasAnyRole('BUYER','SELLER')")
    public PaymentRecord pay(String email, UUID orderId, String gatewayKey, String idempotencyKey, String testCard) {
        PaymentGateway gateway = gateways.select(gatewayKey);
        String key = requireKey(idempotencyKey);
        BuyerOrder order = store.lockBuyerOrder(uid(email), orderId).orElseThrow(PaymentService::notFound);

        // Keys are globally unique. A known key either replays its own order's result or conflicts.
        Optional<PaymentRecord> known = store.findByIdempotencyKey(key);
        if (known.isPresent()) {
            if (!known.get().orderId().equals(orderId)) {
                throw conflict("This idempotency key was already used for another order.");
            }
            return known.get();
        }

        // One payment row per order. Only a FAILED attempt may be retried, and only with a new key.
        Optional<PaymentRecord> existing = store.findByOrder(orderId);
        if (existing.isPresent() && existing.get().status() != PaymentStatus.FAILED) {
            throw conflict("This order already has a " + existing.get().status() + " payment.");
        }
        if (!"PLACED".equals(order.status())) {
            throw conflict("This order can no longer be paid.");
        }

        PaymentResult result = gateway.charge(new PaymentRequest(orderId, order.total(), key, testCard));
        PaymentRecord record = new PaymentRecord(orderId, gateway.name(), order.total(), result.status(), key);
        try {
            if (existing.isPresent()) {
                if (!store.replaceFailed(orderId, record)) {
                    throw conflict("This order already has a payment. Reload the order.");
                }
            } else {
                store.insert(record);
            }
        } catch (DataIntegrityViolationException e) {
            throw conflict("This payment was already recorded. Reload the order.");
        }

        if (record.status() == PaymentStatus.COMPLETED && !store.markOrderProcessing(orderId)) {
            throw conflict("This order can no longer be paid.");
        }
        return record;
    }

    @Transactional(readOnly = true)
    public Optional<PaymentRecord> payment(String email, UUID orderId) {
        store.findBuyerOrder(uid(email), orderId).orElseThrow(PaymentService::notFound);
        return store.findByOrder(orderId);
    }

    private long uid(String email) {
        return users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED))
                .getId();
    }

    private static String requireKey(String idempotencyKey) {
        String key = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (key.isEmpty() || key.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("Idempotency key must be 1 to " + MAX_KEY_LENGTH + " characters.");
        }
        return key;
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    private static ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }
}
