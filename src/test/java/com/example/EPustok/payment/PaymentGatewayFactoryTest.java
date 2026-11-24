package com.example.EPustok.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class PaymentGatewayFactoryTest {

    private final PaymentGatewayFactory factory = new PaymentGatewayFactory(true);
    private final PaymentGatewayFactory withoutMock = new PaymentGatewayFactory(false);

    @Test void factoryReturnsEachGatewayByKey() {
        assertInstanceOf(CodGateway.class, factory.select("COD"));
        assertInstanceOf(MockGateway.class, factory.select("MOCK"));
        assertInstanceOf(BkashGateway.class, factory.select("BKASH"));
        assertInstanceOf(NagadGateway.class, factory.select("nagad"));
        assertEquals("NAGAD", factory.select(" nagad ").name());
    }

    @Test void factoryRejectsUnknownKeyWithIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> factory.select("PAYPAL"));
        assertThrows(IllegalArgumentException.class, () -> factory.select(null));
    }

    @Test void mockIsRejectedWithBadRequestWhenDisabled() {
        assertFalse(withoutMock.isMockEnabled());
        var ex = assertThrows(ResponseStatusException.class, () -> withoutMock.select("MOCK"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Unsupported payment method", ex.getReason());
    }

    @Test void realGatewaysStillWorkWhenMockDisabled() {
        assertInstanceOf(CodGateway.class, withoutMock.select("COD"));
        assertInstanceOf(BkashGateway.class, withoutMock.select("BKASH"));
        assertInstanceOf(NagadGateway.class, withoutMock.select("NAGAD"));
        assertThrows(IllegalArgumentException.class, () -> withoutMock.select("PAYPAL"));
    }

    @Test void defaultConstructorHasMockDisabled() {
        assertFalse(new PaymentGatewayFactory().isMockEnabled());
    }
}
