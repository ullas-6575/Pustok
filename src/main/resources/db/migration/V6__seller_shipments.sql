CREATE TABLE seller_shipment (
order_id UUID NOT NULL REFERENCES purchase_order(id),
seller_id BIGINT NOT NULL REFERENCES identity_users(id),
status VARCHAR(20) NOT NULL DEFAULT 'PLACED' CHECK(status IN ('PLACED','PROCESSING','SHIPPED','DELIVERED')),
PRIMARY KEY(order_id,seller_id));
INSERT INTO seller_shipment(order_id,seller_id,status)
SELECT DISTINCT i.order_id,i.seller_id,o.status FROM order_item i JOIN purchase_order o ON o.id=i.order_id;
CREATE INDEX idx_shipment_seller ON seller_shipment(seller_id);
