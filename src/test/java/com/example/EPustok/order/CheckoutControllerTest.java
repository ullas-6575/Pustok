package com.example.EPustok.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.EPustok.cart.CartService;
import com.example.EPustok.identity.User;
import com.example.EPustok.identity.UserRepository;
import com.example.EPustok.payment.MockGateway;
import com.example.EPustok.payment.PaymentGateway;
import com.example.EPustok.payment.PaymentGatewayFactory;
import com.example.EPustok.payment.PaymentRecord;
import com.example.EPustok.payment.PaymentRequest;
import com.example.EPustok.payment.PaymentResult;
import com.example.EPustok.payment.PaymentService;
import com.example.EPustok.payment.PaymentStatus;
import com.example.EPustok.payment.PaymentStore;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Checkout with payment, end to end through the controller, with no database. CheckoutService and PaymentService
 * are the real classes; JDBC and the payment store are fakes that record what would have been written.
 */
class CheckoutControllerTest {

    private static final String EMAIL = "b@example.com";
    private static final long BUYER_ID = 7L;
    /** Book 11 at 125.50 x 2, computed by CheckoutService from the database row. */
    private static final BigDecimal DB_TOTAL = new BigDecimal("251.00");
    private static final UUID REQUEST = UUID.randomUUID();

    private Db db;
    private RecordingFactory factory;
    private MockMvc mvc;

    @BeforeEach void setUp() throws Exception {
        db = new Db();
        factory = new RecordingFactory();
        mvc = mvcWith(factory);
    }

    /** Builds the controller over the shared fake database with the given factory. */
    private MockMvc mvcWith(RecordingFactory gateways) throws Exception {
        var users = usersWith(BUYER_ID);
        var checkout = new CheckoutService(db.jdbc(), users);
        var payments = new PaymentService(db.store, gateways, users);
        // No template engine here: redirects become real redirects, and any page view renders nothing but keeps its name.
        return MockMvcBuilders.standaloneSetup(new CheckoutController(mock(CartService.class), checkout, payments, gateways))
                .setViewResolvers((name, locale) -> name.startsWith("redirect:")
                        ? new RedirectView(name.substring("redirect:".length()))
                        : (model, request, response) -> { })
                .build();
    }

    @Test void codCheckoutCreatesPlacedOrderWithPendingPayment() throws Exception {
        MvcResult result = mvc.perform(checkout("COD", null))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("message", "Order placed. You will pay cash on delivery."))
                .andReturn();

        UUID order = orderIdFrom(result);
        assertEquals("PLACED", db.store.status(order));
        assertEquals(1, db.store.payments.size());
        assertEquals("COD", db.store.payments.get(0).gateway());
        assertEquals(PaymentStatus.PENDING, db.store.payments.get(0).status());
    }

    @Test void missingGatewayDefaultsToCod() throws Exception {
        mvc.perform(checkout(null, null)).andExpect(status().is3xxRedirection());

        assertEquals("COD", db.store.payments.get(0).gateway());
        assertEquals(PaymentStatus.PENDING, db.store.payments.get(0).status());
    }

    @Test void mockSuccessMovesOrderToProcessingAndCompletesPayment() throws Exception {
        MvcResult result = mvc.perform(checkout("MOCK", null))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("message", "Payment completed. Your order is now processing."))
                .andReturn();

        assertEquals("PROCESSING", db.store.status(orderIdFrom(result)));
        assertEquals(PaymentStatus.COMPLETED, db.store.payments.get(0).status());
    }

    @Test void mockDeclineLeavesOrderPlacedWithFailedPaymentAndRedirectsWithMessage() throws Exception {
        MvcResult result = mvc.perform(checkout("MOCK", MockGateway.DECLINE_MARKER))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("message", "Payment failed. You can retry below."))
                .andReturn();

        assertEquals("PLACED", db.store.status(orderIdFrom(result)));
        assertEquals(PaymentStatus.FAILED, db.store.payments.get(0).status());
    }

    @Test void bkashPaymentIsPendingAndOrderStaysPlaced() throws Exception {
        assertPendingGateway("BKASH");
    }

    @Test void nagadPaymentIsPendingAndOrderStaysPlaced() throws Exception {
        assertPendingGateway("NAGAD");
    }

    @Test void unknownGatewayReShowsCheckoutAndSavesNothing() throws Exception {
        mvc.perform(checkout("PAYPAL", null))
                .andExpect(status().isOk())
                .andExpect(view().name("checkout"))
                .andExpect(model().attribute("message", "Choose a valid payment method."));

        assertTrue(db.writes.isEmpty());
        assertTrue(db.store.orders.isEmpty());
        assertTrue(db.store.payments.isEmpty());
        assertTrue(factory.requests.isEmpty());
    }

    @Test void stockFailureCreatesNoOrderAndNoPayment() throws Exception {
        db.stockResult = 0;

        mvc.perform(checkout("MOCK", null))
                .andExpect(status().isOk())
                .andExpect(view().name("checkout"))
                .andExpect(model().attribute("message", "Stock changed for Book. Update your cart."));

        assertTrue(db.writes.stream().noneMatch(sql -> sql.startsWith("INSERT INTO purchase_order")));
        assertTrue(db.store.orders.isEmpty());
        assertTrue(db.store.payments.isEmpty());
        assertTrue(factory.requests.isEmpty());
    }

    @Test void amountSentToGatewayIsOrderTotalFromDatabaseNotFromRequest() throws Exception {
        mvc.perform(checkout("MOCK", null).param("amount", "1.00").param("total", "0.01"))
                .andExpect(status().is3xxRedirection());

        assertEquals(1, factory.requests.size());
        assertEquals(DB_TOTAL, factory.requests.get(0).amount());
        assertEquals(DB_TOTAL, db.store.payments.get(0).amount());
    }

    @Test void checkoutReplayWithSameRequestIdDoesNotCreateSecondOrderOrCharge() throws Exception {
        UUID first = orderIdFrom(mvc.perform(checkout("MOCK", null).param("requestId", REQUEST.toString()))
                .andExpect(status().is3xxRedirection()).andReturn());
        UUID replay = orderIdFrom(mvc.perform(checkout("MOCK", null).param("requestId", REQUEST.toString()))
                .andExpect(status().is3xxRedirection()).andReturn());

        assertEquals(first, replay);
        assertEquals(1, db.writes.stream().filter(sql -> sql.startsWith("INSERT INTO purchase_order")).count());
        assertEquals(1, db.store.payments.size());
        assertEquals(1, factory.requests.size());
    }

    @Test void replayedCheckoutRedirectsWithAlreadyPlacedMessage() throws Exception {
        mvc.perform(checkout("MOCK", null).param("requestId", REQUEST.toString()))
                .andExpect(status().is3xxRedirection());

        mvc.perform(checkout("MOCK", null).param("requestId", REQUEST.toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("message", "This order was already placed."));
    }

    @Test void expectedTotalThatMatchesCartIsAccepted() throws Exception {
        mvc.perform(checkout("COD", null).param("expectedTotal", "251.00"))
                .andExpect(status().is3xxRedirection());

        assertEquals(1, db.store.orders.size());
    }

    @Test void staleExpectedTotalReShowsCheckoutAndCreatesNothing() throws Exception {
        mvc.perform(checkout("COD", null).param("expectedTotal", "250.00"))
                .andExpect(status().isOk())
                .andExpect(view().name("checkout"))
                .andExpect(model().attributeExists("message"));

        assertTrue(db.writes.stream().noneMatch(sql -> sql.startsWith("INSERT INTO purchase_order")));
        assertTrue(db.store.orders.isEmpty());
        assertTrue(factory.requests.isEmpty());
    }

    @Test void mockPaymentIsRefusedWhenMockIsDisabled() throws Exception {
        var disabled = new RecordingFactory(false);

        mvcWith(disabled).perform(checkout("MOCK", null))
                .andExpect(status().isOk())
                .andExpect(view().name("checkout"))
                .andExpect(model().attribute("message", "Choose a valid payment method."));

        assertTrue(db.writes.isEmpty());
        assertTrue(disabled.requests.isEmpty());
    }

    @Test void checkoutPageExposesMockFlag() throws Exception {
        mvc.perform(get("/buyer/checkout").principal(new UsernamePasswordAuthenticationToken(EMAIL, "pw")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("mockPaymentsEnabled", true));

        mvcWith(new RecordingFactory(false))
                .perform(get("/buyer/checkout").principal(new UsernamePasswordAuthenticationToken(EMAIL, "pw")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("mockPaymentsEnabled", false));
    }

    @Test void confirmationShowsAwaitingNoticeForPendingOnlinePayment() throws Exception {
        UUID order = orderIdFrom(mvc.perform(checkout("BKASH", null))
                .andExpect(status().is3xxRedirection()).andReturn());

        mvc.perform(get("/buyer/orders/{id}", order).principal(new UsernamePasswordAuthenticationToken(EMAIL, "pw")))
                .andExpect(status().isOk())
                .andExpect(view().name("order-confirmation"))
                .andExpect(model().attribute("mockPaymentsEnabled", true))
                .andExpect(model().attribute("paymentNotice", "Payment is awaiting confirmation. Follow your order status below."))
                .andExpect(model().attribute("paymentLabel", "Payment: bKash (sandbox), pending confirmation"));
    }

    @Test void confirmationShowsDueOnDeliveryNoticeForCod() throws Exception {
        UUID order = orderIdFrom(mvc.perform(checkout("COD", null))
                .andExpect(status().is3xxRedirection()).andReturn());

        mvc.perform(get("/buyer/orders/{id}", order).principal(new UsernamePasswordAuthenticationToken(EMAIL, "pw")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("paymentNotice", "Payment is due on delivery. Follow your order status below."));
    }

    private void assertPendingGateway(String gateway) throws Exception {
        MvcResult result = mvc.perform(checkout(gateway, null))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("message",
                        "Payment is pending confirmation. Your order stays placed until it is confirmed."))
                .andReturn();

        assertEquals("PLACED", db.store.status(orderIdFrom(result)));
        assertEquals(gateway, db.store.payments.get(0).gateway());
        assertEquals(PaymentStatus.PENDING, db.store.payments.get(0).status());
    }

    private MockHttpServletRequestBuilder checkout(String gateway, String testCard) {
        var request = post("/buyer/checkout")
                .principal(new UsernamePasswordAuthenticationToken(EMAIL, "pw"))
                .param("name", "Recipient")
                .param("phone", "01234567890")
                .param("address", "Dhaka");
        if (gateway != null) {
            request.param("gateway", gateway);
        }
        if (testCard != null) {
            request.param("testCard", testCard);
        }
        return request;
    }

    private static UUID orderIdFrom(MvcResult result) {
        String url = result.getResponse().getRedirectedUrl();
        return UUID.fromString(url.substring(url.lastIndexOf('/') + 1));
    }

    /** Wraps the real factory and records every charge that reaches a gateway. */
    static final class RecordingFactory extends PaymentGatewayFactory {
        final List<PaymentRequest> requests = new ArrayList<>();

        /** MOCK enabled, so the flows above can charge the test card. */
        RecordingFactory() { super(true); }

        RecordingFactory(boolean mockEnabled) { super(mockEnabled); }

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

    /**
     * Fake JDBC for CheckoutService. The cart holds book 11 (seller 8) at 125.50 x 2. An INSERT into purchase_order
     * also registers the order in the payment store, as the real table would make it visible to PaymentService.
     */
    static final class Db {
        final FakeStore store = new FakeStore();
        final Map<UUID, UUID> orderByRequest = new HashMap<>();
        final List<String> writes = new ArrayList<>();
        final Map<UUID, Map<String, Object>> orderRows = new HashMap<>();
        int stockResult = 1;

        JdbcTemplate jdbc() {
            return mock(JdbcTemplate.class, (Answer<Object>) this::answer);
        }

        private Object answer(InvocationOnMock invocation) throws Throwable {
            Object[] args = invocation.getArguments();
            String name = invocation.getMethod().getName();
            if ("update".equals(name) && args[0] instanceof String sql) {
                writes.add(sql);
                if (sql.startsWith("UPDATE book SET stock")) {
                    return stockResult;
                }
                if (sql.startsWith("INSERT INTO purchase_order")) {
                    // INSERT columns: id, buyer_id, recipient_name, phone, address, total, request_id
                    UUID id = (UUID) args[1];
                    orderByRequest.put((UUID) args[7], id);
                    store.addOrder(id, BUYER_ID, (BigDecimal) args[6]);
                    orderRows.put(id, Map.of("id", id, "status", "PLACED", "recipient_name", args[2],
                            "phone", args[3], "address", args[4], "total", args[5]));
                }
                return 1;
            }
            if ("queryForList".equals(name) && args[0] instanceof String sql && sql.startsWith("SELECT * FROM purchase_order")) {
                // CheckoutService.order(): (id, buyerId)
                Map<String, Object> row = orderRows.get((UUID) args[1]);
                return row == null ? List.of() : List.of(row);
            }
            if ("query".equals(name) && args.length > 1 && args[1] instanceof RowMapper<?> mapper) {
                String sql = (String) args[0];
                if (sql.contains("FROM purchase_order")) {
                    // Replay lookup: (buyer, requestId)
                    UUID existing = orderByRequest.get((UUID) args[3]);
                    return existing == null ? List.of() : List.of(existing);
                }
                if (sql.contains("FROM cart_item")) {
                    return List.of(mapper.mapRow(bookRow(), 1));
                }
                return List.of();
            }
            return Answers.RETURNS_DEFAULTS.answer(invocation);
        }

        private static ResultSet bookRow() throws Exception {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getLong(1)).thenReturn(11L);
            when(rs.getLong(2)).thenReturn(8L);
            when(rs.getString(3)).thenReturn("Book");
            when(rs.getBigDecimal(4)).thenReturn(new BigDecimal("125.50"));
            when(rs.getInt(5)).thenReturn(2);
            return rs;
        }
    }

    /** In-memory PaymentStore for the flows above. The UNIQUE rules are enforced by PaymentService itself. */
    static final class FakeStore implements PaymentStore {
        final Map<UUID, BuyerOrder> orders = new HashMap<>();
        final Map<UUID, Long> buyerOf = new HashMap<>();
        final List<PaymentRecord> payments = new ArrayList<>();

        void addOrder(UUID id, long buyer, BigDecimal total) {
            orders.put(id, new BuyerOrder(id, total, "PLACED"));
            buyerOf.put(id, buyer);
        }

        String status(UUID id) {
            return orders.get(id).status();
        }

        @Override public Optional<BuyerOrder> findBuyerOrder(long buyerId, UUID orderId) {
            if (!buyerOf.containsKey(orderId) || buyerOf.get(orderId) != buyerId) {
                return Optional.empty();
            }
            return Optional.ofNullable(orders.get(orderId));
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
            payments.add(payment);
        }

        @Override public boolean replaceFailed(UUID orderId, PaymentRecord payment) {
            for (int i = 0; i < payments.size(); i++) {
                if (payments.get(i).orderId().equals(orderId) && payments.get(i).status() == PaymentStatus.FAILED) {
                    payments.set(i, payment);
                    return true;
                }
            }
            return false;
        }

        @Override public boolean markOrderProcessing(UUID orderId) {
            BuyerOrder order = orders.get(orderId);
            if (order == null || !"PLACED".equals(order.status())) {
                return false;
            }
            orders.put(orderId, new BuyerOrder(orderId, order.total(), "PROCESSING"));
            return true;
        }
    }

    private static UserRepository usersWith(long id) throws Exception {
        var user = new User("Buyer", EMAIL, "hash");
        Field field = User.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(user, id);
        var users = mock(UserRepository.class);
        when(users.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
        return users;
    }
}
