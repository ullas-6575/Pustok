CREATE TABLE cart_item (
 user_id BIGINT NOT NULL REFERENCES identity_users(id) ON DELETE CASCADE,
 book_id BIGINT NOT NULL REFERENCES book(id) ON DELETE CASCADE,
 quantity INTEGER NOT NULL CHECK(quantity>0),
 PRIMARY KEY(user_id,book_id)
);
CREATE INDEX idx_cart_item_book ON cart_item(book_id);
