package com.example.EPustok.payment;

/**
 * Test gateway. Deterministic and offline: it completes every charge unless the request carries the
 * decline marker, in which case the charge fails.
 */
public class MockGateway implements PaymentGateway {
    public static final String DECLINE_MARKER = "DECLINE";

    @Override
    public String name() {
        return "MOCK";
    }

    @Override
    public PaymentResult charge(PaymentRequest request) {
        boolean declined = DECLINE_MARKER.equals(request.testCard());
        return new PaymentResult(declined ? PaymentStatus.FAILED : PaymentStatus.COMPLETED);
    }
}
