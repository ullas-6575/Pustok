package com.example.EPustok.payment;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPaymentStore implements PaymentStore {
    private final JdbcTemplate jdbc;

    public JdbcPaymentStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String ORDER_COLUMNS = "SELECT id,total,status FROM purchase_order WHERE id=? AND buyer_id=?";
    private static final String PAYMENT_COLUMNS = "SELECT order_id,gateway,amount,status,idempotency_key FROM payment_payments ";

    @Override
    public Optional<BuyerOrder> findBuyerOrder(long buyerId, UUID orderId) {
        return jdbc.query(ORDER_COLUMNS, (r, n) -> new BuyerOrder(r.getObject(1, UUID.class), r.getBigDecimal(2), r.getString(3)), orderId, buyerId)
                .stream().findFirst();
    }

    @Override
    public Optional<BuyerOrder> lockBuyerOrder(long buyerId, UUID orderId) {
        return jdbc.query(ORDER_COLUMNS + " FOR UPDATE", (r, n) -> new BuyerOrder(r.getObject(1, UUID.class), r.getBigDecimal(2), r.getString(3)), orderId, buyerId)
                .stream().findFirst();
    }

    @Override
    public Optional<PaymentRecord> findByIdempotencyKey(String idempotencyKey) {
        return jdbc.query(PAYMENT_COLUMNS + "WHERE idempotency_key=?", JdbcPaymentStore::payment, idempotencyKey)
                .stream().findFirst();
    }

    @Override
    public Optional<PaymentRecord> findByOrder(UUID orderId) {
        return jdbc.query(PAYMENT_COLUMNS + "WHERE order_id=?", JdbcPaymentStore::payment, orderId)
                .stream().findFirst();
    }

    @Override
    public void insert(PaymentRecord p) {
        jdbc.update("INSERT INTO payment_payments(order_id,gateway,amount,status,idempotency_key) VALUES (?,?,?,?,?)",
                p.orderId(), p.gateway(), p.amount(), p.status().name(), p.idempotencyKey());
    }

    @Override
    public boolean replaceFailed(UUID orderId, PaymentRecord p) {
        return jdbc.update(
                "UPDATE payment_payments SET gateway=?,amount=?,status=?,idempotency_key=?,version=version+1 WHERE order_id=? AND status='FAILED'",
                p.gateway(), p.amount(), p.status().name(), p.idempotencyKey(), orderId) == 1;
    }

    @Override
    public boolean markOrderProcessing(UUID orderId) {
        return jdbc.update("UPDATE purchase_order SET status='PROCESSING' WHERE id=? AND status='PLACED'", orderId) == 1;
    }

    private static PaymentRecord payment(java.sql.ResultSet r, int n) throws java.sql.SQLException {
        return new PaymentRecord(r.getObject(1, UUID.class), r.getString(2), r.getBigDecimal(3),
                PaymentStatus.valueOf(r.getString(4)), r.getString(5));
    }
}
