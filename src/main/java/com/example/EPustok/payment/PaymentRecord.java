package com.example.EPustok.payment;

import java.math.BigDecimal;
import java.util.UUID;

/** One row of payment_payments. */
public record PaymentRecord(UUID orderId, String gateway, BigDecimal amount, PaymentStatus status, String idempotencyKey) {}
