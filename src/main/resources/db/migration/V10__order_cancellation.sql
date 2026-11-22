-- Buyer cancellation: a CANCELLED status for shipments and orders, plus a resell listing reference
-- so a cancelled used-book purchase can reopen its listing.
ALTER TABLE seller_shipment DROP CONSTRAINT seller_shipment_status_check;
ALTER TABLE seller_shipment ADD CONSTRAINT seller_shipment_status_check
    CHECK (status IN ('PLACED', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED'));

ALTER TABLE purchase_order ADD CONSTRAINT purchase_order_status_check
    CHECK (status IN ('PLACED', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED'));

ALTER TABLE order_item ADD COLUMN listing_id BIGINT REFERENCES marketplace_resell_listings(id) ON DELETE SET NULL;
CREATE INDEX idx_order_item_listing ON order_item(listing_id);
