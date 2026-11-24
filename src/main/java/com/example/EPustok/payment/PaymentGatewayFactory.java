package com.example.EPustok.payment;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Factory: the only place that knows the concrete gateway classes. An unknown key is rejected with
 * IllegalArgumentException, which PaymentController maps to HTTP 400. MOCK completes charges without money,
 * so it is registered only when payment.mock-enabled is true; otherwise it is rejected here.
 */
@Component
public class PaymentGatewayFactory {
    private final Map<String, PaymentGateway> gateways;
    private final boolean mockEnabled;

    /** Mock disabled. */
    public PaymentGatewayFactory() {
        this(false);
    }

    @Autowired
    public PaymentGatewayFactory(@Value("${payment.mock-enabled:false}") boolean mockEnabled) {
        this.mockEnabled = mockEnabled;
        Map<String, PaymentGateway> map = new HashMap<>();
        map.put("COD", new CodGateway());
        if (mockEnabled) {
            map.put("MOCK", new MockGateway());
        }
        map.put("BKASH", new BkashGateway());
        map.put("NAGAD", new NagadGateway());
        this.gateways = Map.copyOf(map);
    }

    public boolean isMockEnabled() {
        return mockEnabled;
    }

    public PaymentGateway select(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Choose a payment gateway.");
        }
        String normalized = key.trim().toUpperCase(Locale.ROOT);
        if ("MOCK".equals(normalized) && !mockEnabled) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported payment method");
        }
        PaymentGateway gateway = gateways.get(normalized);
        if (gateway == null) {
            throw new IllegalArgumentException("Unknown payment gateway: " + key);
        }
        return gateway;
    }
}
