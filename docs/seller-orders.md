# Seller fulfillment

/seller/orders lists only the authenticated seller's shipments and own items, with delivery contact details. Sellers advance PLACED -> PROCESSING -> SHIPPED. Admins confirm DELIVERED and can manage individual shipments from /admin/orders/{id}. Buyer order detail shows each item's shipment status.

V6 backfills shipments for existing orders using their current status. New checkout creates one shipment per seller. Whole-order status is the least advanced shipment: all shipments must reach a stage before the order reaches it. Updates lock the parent order and require the previous shipment state, preventing concurrent updates from losing aggregate progress. Admin whole-order updates remain available only when all shipments share the required previous stage. Cancellation remains future work.
