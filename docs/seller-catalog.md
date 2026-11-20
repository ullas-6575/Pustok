# Seller approval and catalog

BUYER: open /buyer/seller-application (Become a Seller on home), submit shop name, phone and address. Pending applications cannot be duplicated; rejected applicants can apply again.

ADMIN: /admin/applications approves or rejects pending applications. Approval assigns SELLER; JWT authentication reloads the current database role on subsequent requests. Direct role assignment cannot grant SELLER.

ADMIN: /admin/categories creates, renames and deletes categories. Categories containing books cannot be deleted.

Approved SELLER: /seller/books creates, reads, updates and deletes their own books with title, author, BDT price, stock and category. Other sellers cannot modify these books. A seller role without an approved profile cannot manage books.

Homepage displays stored books and an empty state. No demo books are inserted. V3 Flyway migration creates seller_profile, category and book tables.

Validation: Maven full suite passed against the configured PostgreSQL database; approval/rejection unit tests added. Browser visual review has not been performed.

Approved sellers retain access to buyer routes and the My account navigation. Seller applications remain BUYER-only.
