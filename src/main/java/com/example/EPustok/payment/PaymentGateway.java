package com.example.EPustok.payment;

/** Strategy: one implementation per payment method. Implementations must not perform network I/O in this milestone. */
public interface PaymentGateway {
    /** Gateway key as stored in payment_payments.gateway and sent by the form, e.g. COD or MOCK. */
    String name();

    PaymentResult charge(PaymentRequest request);
}
