CREATE TABLE resell_cart_item (
    user_id BIGINT NOT NULL REFERENCES identity_users(id) ON DELETE CASCADE,
    listing_id BIGINT NOT NULL REFERENCES marketplace_resell_listings(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, listing_id)
);

CREATE INDEX idx_resell_cart_listing ON resell_cart_item(listing_id);
