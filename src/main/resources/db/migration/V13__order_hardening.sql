-- Order hardening. Payment amounts get the same precision as order totals (NUMERIC(18,2), V5).
-- Index on order_item.book_id for the book FK (ON DELETE SET NULL) and the stock restore join in OrderTracking.cancel.
ALTER TABLE payment_payments ALTER COLUMN amount TYPE NUMERIC(18,2);
CREATE INDEX IF NOT EXISTS idx_order_item_book ON order_item(book_id);
