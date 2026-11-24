package com.example.EPustok.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentGatewayTest {

    private static PaymentRequest request(String testCard) {
        return new PaymentRequest(UUID.randomUUID(), new BigDecimal("251.00"), "key-1", testCard);
    }

    @Test void mockGatewayCompletesOrdinaryPayment() {
        assertEquals(PaymentStatus.COMPLETED, new MockGateway().charge(request(null)).status());
    }

    @Test void mockGatewayFailsForDeclineTestCard() {
        assertEquals(PaymentStatus.FAILED, new MockGateway().charge(request(MockGateway.DECLINE_MARKER)).status());
    }

    @Test void codGatewayReturnsPendingAndDoesNotCompleteOnCharge() {
        assertEquals(PaymentStatus.PENDING, new CodGateway().charge(request(null)).status());
    }

    @Test void bkashGatewayReturnsPendingWithoutNetwork() {
        assertEquals(PaymentStatus.PENDING, new BkashGateway().charge(request(null)).status());
    }

    @Test void nagadGatewayReturnsPendingWithoutNetwork() {
        assertEquals(PaymentStatus.PENDING, new NagadGateway().charge(request(null)).status());
    }
}
