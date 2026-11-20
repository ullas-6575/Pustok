# Order tracking

Buyers and sellers can view their purchases at /buyer/orders and reopen the private detail page. Lists show newest first with 20 orders per page. Existing checkout orders are included automatically.

Admins use /admin/orders to see all orders and delivery details. On each detail page an admin can advance status: PLACED -> PROCESSING -> SHIPPED -> DELIVERED. Updates require the previous state in the SQL predicate, preventing stale updates and skipped stages. Status updates use CSRF-protected POST. Buyers cannot update status or access another buyer's order. Seller-specific fulfillment remains a future feature; admin currently manages whole-order status.
