package com.example.EPustok.payment;

/** Cash on delivery: nothing is charged now. The payment stays PENDING until delivery records it. */
public class CodGateway implements PaymentGateway {
    @Override
    public String name() {
        return "COD";
    }

    @Override
    public PaymentResult charge(PaymentRequest request) {
        return new PaymentResult(PaymentStatus.PENDING);
    }
}
