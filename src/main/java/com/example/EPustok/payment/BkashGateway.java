package com.example.EPustok.payment;

/**
 * Sandbox-shaped stub for bKash. It makes no network call and reads no credentials, so the result is
 * always PENDING until a real sandbox integration replaces it.
 */
public class BkashGateway implements PaymentGateway {
    @Override
    public String name() {
        return "BKASH";
    }

    @Override
    public PaymentResult charge(PaymentRequest request) {
        return new PaymentResult(PaymentStatus.PENDING);
    }
}
