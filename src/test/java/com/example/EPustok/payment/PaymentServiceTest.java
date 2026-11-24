package com.example.EPustok.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.EPustok.identity.User;
import com.example.EPustok.identity.UserRepository;
import com.example.EPustok.payment.PaymentStore.BuyerOrder;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class PaymentServiceTest {

    private static final String BUYER = "buyer@example.com";
    private static final String OTHER = "other@example.com";
    private static final long BUYER_ID = 7L;
    private static final long OTHER_ID = 8L;
    private static final BigDecimal TOTAL = new BigDecimal("251.00");
    private static final String K1 = "key-1";
    private static final String K2 = "key-2";

    private final UUID order = UUID.randomUUID();
    private InMemoryPaymentStore store;
    private RecordingFactory factory;
    private PaymentService service;

    @BeforeEach void setUp() throws Exception {
        store = new InMemoryPaymentStore();
        factory = new RecordingFactory();
        service = new PaymentService(store, factory, users(Map.of(BUYER, BUYER_ID, OTHER, OTHER_ID)));
        store.addOrder(order, BUYER_ID, TOTAL, "PLACED");
    }

    @Test void unknownGatewayKeyIsRejectedAndNothingIsSaved() {
        assertThrows(IllegalArgumentException.class, () -> service.pay(BUYER, order, "PAYPAL", K1, null));
        assertTrue(store.payments.isEmpty());
        assertEquals(0, factory.requests.size());
    }

    @Test void replayOfSameKeyReturnsFirstResultAndSavesOnce() {
        var first = service.pay(BUYER, order, "MOCK", K1, null);
        var replay = service.pay(BUYER, order, "MOCK", K1, null);

        assertEquals(first, replay);
        assertEquals(PaymentStatus.COMPLETED, replay.status());
        assertEquals(1, store.payments.size());
        assertEquals(1, factory.requests.size());
    }

    @Test void replayOfFailedKeyReturnsFailureWithoutChargingAgain() {
        service.pay(BUYER, order, "MOCK", K1, MockGateway.DECLINE_MARKER);
        var replay = service.pay(BUYER, order, "MOCK", K1, MockGateway.DECLINE_MARKER);

        assertEquals(PaymentStatus.FAILED, replay.status());
        assertEquals(1, store.payments.size());
        assertEquals(1, factory.requests.size());
    }

    @Test void sameKeyOnAnotherOrderIsRejectedWith409() {
        UUID other = UUID.randomUUID();
        store.addOrder(other, BUYER_ID, new BigDecimal("10.00"), "PLACED");
        service.pay(BUYER, order, "COD", K1, null);

        var ex = assertThrows(ResponseStatusException.class, () -> service.pay(BUYER, other, "COD", K1, null));
        assertEquals(HttpStatus.CONFLICT.value(), ex.getStatusCode().value());
        assertEquals(1, store.payments.size());
    }

    @Test void completedPaymentCannotBeChargedAgainWith409() {
        service.pay(BUYER, order, "MOCK", K1, null);

        var ex = assertThrows(ResponseStatusException.class, () -> service.pay(BUYER, order, "MOCK", K2, null));
        assertEquals(HttpStatus.CONFLICT.value(), ex.getStatusCode().value());
        assertEquals(1, factory.requests.size());
        assertEquals(1, store.payments.size());
    }

    @Test void failedPaymentCanBeRetriedWithNewKey() {
        var failed = service.pay(BUYER, order, "MOCK", K1, MockGateway.DECLINE_MARKER);
        assertEquals(PaymentStatus.FAILED, failed.status());

        var retry = service.pay(BUYER, order, "MOCK", K2, null);

        assertEquals(PaymentStatus.COMPLETED, retry.status());
        assertEquals(1, store.payments.size());
        assertEquals(K2, store.payments.get(0).idempotencyKey());
        assertEquals("PROCESSING", store.orders.get(order).status());
    }

    @Test void anotherBuyerCannotPayOrderWith404() {
        var ex = assertThrows(ResponseStatusException.class, () -> service.pay(OTHER, order, "MOCK", K1, null));
        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getStatusCode().value());
        assertTrue(store.payments.isEmpty());
        assertEquals(0, factory.requests.size());
    }

    @Test void amountIsTakenFromOrderTotalNotRequest() {
        service.pay(BUYER, order, "COD", K1, null);

        assertEquals(TOTAL, factory.requests.get(0).amount());
        assertEquals(TOTAL, store.payments.get(0).amount());
    }

    @Test void completedMockPaymentMovesPlacedOrderToProcessing() {
        service.pay(BUYER, order, "MOCK", K1, null);

        assertEquals("PROCESSING", store.orders.get(order).status());
    }

    @Test void codPaymentIsPendingAndLeavesOrderPlaced() {
        var payment = service.pay(BUYER, order, "COD", K1, null);

        assertEquals(PaymentStatus.PENDING, payment.status());
        assertEquals("PLACED", store.orders.get(order).status());
    }

    @Test void failedPaymentLeavesOrderUnpaidAndPlaced() {
        service.pay(BUYER, order, "MOCK", K1, MockGateway.DECLINE_MARKER);

        assertEquals("PLACED", store.orders.get(order).status());
    }

    @Test void pendingPaymentBlocksNewAttemptWith409() {
        service.pay(BUYER, order, "COD", K1, null);

        var ex = assertThrows(ResponseStatusException.class, () -> service.pay(BUYER, order, "MOCK", K2, null));
        assertEquals(HttpStatus.CONFLICT.value(), ex.getStatusCode().value());
        assertEquals(1, store.payments.size());
    }

    @Test void mockGatewayIsRejectedWhenDisabledAndNothingIsCharged() throws Exception {
        var disabled = new PaymentService(store, new PaymentGatewayFactory(false), users(Map.of(BUYER, BUYER_ID)));

        var ex = assertThrows(ResponseStatusException.class, () -> disabled.pay(BUYER, order, "MOCK", K1, null));
        assertEquals(HttpStatus.BAD_REQUEST.value(), ex.getStatusCode().value());
        assertEquals("Unsupported payment method", ex.getReason());
        assertTrue(store.payments.isEmpty());
        assertEquals("PLACED", store.orders.get(order).status());
    }

    @Test void cancelledOrderCannotBePaidWith409() {
        UUID cancelled = UUID.randomUUID();
        store.addOrder(cancelled, BUYER_ID, new BigDecimal("10.00"), "CANCELLED");

        var ex = assertThrows(ResponseStatusException.class, () -> service.pay(BUYER, cancelled, "MOCK", K1, null));
        assertEquals(HttpStatus.CONFLICT.value(), ex.getStatusCode().value());
        assertTrue(store.payments.isEmpty());
    }

    // ---- helpers ----

    /** Wraps the real factory and records each request that reaches a gateway, so tests can count charges. */
    static final class RecordingFactory extends PaymentGatewayFactory {
        final List<PaymentRequest> requests = new ArrayList<>();

        RecordingFactory() {
            super(true);
        }

        @Override
        public PaymentGateway select(String key) {
            PaymentGateway real = super.select(key);
            return new PaymentGateway() {
                @Override public String name() { return real.name(); }
                @Override public PaymentResult charge(PaymentRequest request) {
                    requests.add(request);
                    return real.charge(request);
                }
            };
        }
    }

    /** In-memory PaymentStore. It enforces the same UNIQUE rules as V12 so the service logic is exercised. */
    static final class InMemoryPaymentStore implements PaymentStore {
        final Map<UUID, BuyerOrder> orders = new HashMap<>();
        final Map<UUID, Long> buyerOf = new HashMap<>();
        final List<PaymentRecord> payments = new ArrayList<>();

        void addOrder(UUID id, long buyer, BigDecimal total, String status) {
            orders.put(id, new BuyerOrder(id, total, status));
            buyerOf.put(id, buyer);
        }

        @Override public Optional<BuyerOrder> findBuyerOrder(long buyerId, UUID orderId) {
            return buyerOf.containsKey(orderId) && buyerOf.get(orderId) == buyerId
                    ? Optional.ofNullable(orders.get(orderId)) : Optional.empty();
        }

        @Override public Optional<BuyerOrder> lockBuyerOrder(long buyerId, UUID orderId) {
            return findBuyerOrder(buyerId, orderId);
        }

        @Override public Optional<PaymentRecord> findByIdempotencyKey(String idempotencyKey) {
            return payments.stream().filter(p -> p.idempotencyKey().equals(idempotencyKey)).findFirst();
        }

        @Override public Optional<PaymentRecord> findByOrder(UUID orderId) {
            return payments.stream().filter(p -> p.orderId().equals(orderId)).findFirst();
        }

        @Override public void insert(PaymentRecord payment) {
            if (findByOrder(payment.orderId()).isPresent() || findByIdempotencyKey(payment.idempotencyKey()).isPresent()) {
                throw new DuplicateKeyException("unique constraint violated");
            }
            payments.add(payment);
        }

        @Override public boolean replaceFailed(UUID orderId, PaymentRecord payment) {
            for (int i = 0; i < payments.size(); i++) {
                PaymentRecord p = payments.get(i);
                if (p.orderId().equals(orderId) && p.status() == PaymentStatus.FAILED) {
                    payments.set(i, payment);
                    return true;
                }
            }
            return false;
        }

        @Override public boolean markOrderProcessing(UUID orderId) {
            BuyerOrder o = orders.get(orderId);
            if (o == null || !"PLACED".equals(o.status())) {
                return false;
            }
            orders.put(orderId, new BuyerOrder(o.id(), o.total(), "PROCESSING"));
            return true;
        }
    }

    private static UserRepository users(Map<String, Long> idsByEmail) throws Exception {
        var repo = mock(UserRepository.class);
        for (var entry : idsByEmail.entrySet()) {
            var user = new User("Name", entry.getKey(), "hash");
            Field id = User.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(user, entry.getValue());
            when(repo.findByEmailIgnoreCase(entry.getKey())).thenReturn(Optional.of(user));
        }
        return repo;
    }
}
