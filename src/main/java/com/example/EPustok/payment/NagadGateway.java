package com.example.EPustok.payment;

/**
 * Sandbox-shaped stub for Nagad. It makes no network call and reads no credentials, so the result is
 * always PENDING until a real sandbox integration replaces it.
 */
public class NagadGateway implements PaymentGateway {
    @Override
    public String name() {
        return "NAGAD";
    }

    @Override
    public PaymentResult charge(PaymentRequest request) {
        return new PaymentResult(PaymentStatus.PENDING);
    }
}
