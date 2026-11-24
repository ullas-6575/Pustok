package com.example.EPustok.payment;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence seam for PaymentService. JdbcPaymentStore is the only implementation at runtime; unit tests
 * use an in-memory fake, so PaymentService can be tested without a database.
 */
public interface PaymentStore {

    record BuyerOrder(UUID id, BigDecimal total, String status) {}

    Optional<BuyerOrder> findBuyerOrder(long buyerId, UUID orderId);

    /** Same as findBuyerOrder, but takes a row lock that serializes payments for this order. */
    Optional<BuyerOrder> lockBuyerOrder(long buyerId, UUID orderId);

    Optional<PaymentRecord> findByIdempotencyKey(String idempotencyKey);

    Optional<PaymentRecord> findByOrder(UUID orderId);

    /** Throws DataIntegrityViolationException when order_id or idempotency_key already exists. */
    void insert(PaymentRecord payment);

    /** Replaces the order's FAILED row with a new attempt. Returns false when the row is no longer FAILED. */
    boolean replaceFailed(UUID orderId, PaymentRecord payment);

    /** Guarded update: moves the order PLACED to PROCESSING only once. Returns false if it was not PLACED. */
    boolean markOrderProcessing(UUID orderId);
}
