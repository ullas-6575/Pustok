# Shopping cart

Branch: feature/shopping-cart.

BUYER and SELLER accounts can open /buyer/cart. Homepage Add to cart adds one copy; adding again increases quantity. Cart supports quantity updates, removal, subtotals and a BDT total using current database prices.

All writes use POST and CSRF protection. User identity comes from authentication. Cart mutations are scoped to that user. Positive quantities, available stock and seller ownership are checked. Concurrent additions for one user serialize through a database row lock. Stock is not reserved or decremented by the cart. Deleted books disappear through a cascading foreign key. Checkout and order creation remain future work.

V4 creates cart_item with a user/book composite primary key.
