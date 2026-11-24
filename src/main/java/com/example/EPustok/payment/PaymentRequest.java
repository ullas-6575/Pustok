package com.example.EPustok.payment;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What a gateway receives. The amount always comes from the order total in the database; nothing in
 * this record is read from the HTTP request except the idempotency key and the optional test card marker.
 */
public record PaymentRequest(UUID orderId, BigDecimal amount, String idempotencyKey, String testCard) {}
