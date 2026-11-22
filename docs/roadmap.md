# Roadmap to completion

## Milestone 1: Authentication foundation

- [x] Supabase connection, user migration, browser signup/signin/logout.
- [x] Demo landing page.
- [x] Fix session fixation protection, anonymous login redirect, concurrent duplicate signup and password byte validation. Tests: AuthFlowTest (rotation, /seller redirect to /signin, duplicate race, UTF-8 byte limit), RegistrationServiceTest.
- [x] Consolidate logout handling; remove obsolete API allowlist entries. Logout lives only in JwtService.logout. No allowlist entry was obsolete: each /api/auth path maps to a handler (AuthFlowTest.permittedApiAuthPathsAllHaveHandlers).
- [x] Move registration business rules into a service. RegistrationService (RegistrationServiceTest).
- [x] Decide session/JWT and single-role/multi-role requirements. Recorded in architecture.md (Decisions) and docs/authentication.md.
- [x] Add controlled admin provisioning and authorization tests.

Exit: registration, failed login, session renewal, CSRF rejection and logout work reliably.

## Milestone 2: Seller and catalog

- [x] Seller application, profile and admin approval. MarketSecurityTest: buyerCanApplyButSellerCannotReapply, adminApprovesBuyerApplicationAndGrantsSellerRole, adminDecisionOnUnknownOrAlreadyReviewedApplicationFails, buyerCannotDecideSellerApplications; MarketServiceTest.
- [x] Category CRUD for admins. MarketSecurityTest: adminCreatesAndDeletesEmptyCategory, categoryContainingBooksCannotBeDeleted, adminRenamesCategoryAndRejectsUnknownOrBlankName, buyerCannotChangeCategories.
- [x] Book CRUD for approved sellers, with ownership checks. MarketSecurityTest: approvedSellerCanEditOwnBookButNotAnotherSellersBook, ownerDeletesOwnBook, sellerBookNotFoundForUnknownId, buyerCannotReachSellerBookRoutes, sellerWithoutApprovedProfileCannotListOrCreateBooks, bookFormRejectsInvalidPriceWithoutSaving.
- [x] Real catalog, search, pagination and book details. CatalogPagingTest, CatalogSearchTest (title/author LIKE with escaped wildcards), CatalogControllerTest.
- [ ] Replace demo home content with real data and empty states. PARTIAL: HomeControllerTest proves the model comes from the books table, and home.html has an empty-state message, but no test renders the template.

Exit: approved sellers list books; other sellers cannot modify those listings.

## Milestone 3: Checkout

- [x] Buyer-owned cart and quantity updates. CartServiceTest: updateIsScopedToCallersCartItem, updateWithinStockPersistsQuantity, addRejectsCumulativeQuantityAboveStock, blocksOwnBooksAndStockOverflow.
- [x] Server-calculated totals and order item price snapshots. CheckoutServiceTest.successfulCheckoutTotalsFromDatabasePricesAndSnapshotsThem; checkout takes no price input.
- [ ] Transactional checkout and concurrency-safe stock reservation. Rollback on mid-checkout failure is unit-tested (CheckoutRollbackTest, real Spring transaction proxy). BLOCKED: row-level concurrency needs Testcontainers (Day 2, CheckoutServiceIT).
- [ ] Order history, shipping and cancellation transitions. Buyer cancellation is unit-tested (OrderTrackingTest: success, 409 for PROCESSING, shipment started and SHIPPED, 404 for another buyer). BLOCKED: migration V10 has not been applied to a database (Day 2).

Exit: checkout cannot oversell; buyers cannot access others' carts/orders.

## Milestone 4: Payments and marketplace

- [ ] Mock/COD payment strategies and gateway factory.
- [ ] Payment idempotency and retry behavior.
- [ ] Resale listing CRUD and ownership checks.
- [ ] Exchange offer state transitions.
- [ ] Notifications after successful business transaction commits.

Exit: list → buy → pay → notify works without duplicate payments.

## Milestone 5: Delivery

- [ ] Isolated unit/integration tests and architecture checks. Unit (`*Test`, surefire) and integration (`*IT`, failsafe) split is in place; the ITs need a real database (Day 2).
- [ ] Dockerfile and compose with local PostgreSQL.
- [ ] PR CI, reviewed merges and branch protection.
- [ ] Actual API documentation, ER diagram and implemented design pattern links.
- [ ] Deployment, HTTPS, secrets and health checks.
- [ ] Final README, team contributions, test reports and demo rehearsal.

Original plan targets at least 20 unit and 10 integration tests, with 31/13 as the goal. Confirm against the actual rubric; record measured counts.

Original ownership: A identity/common/notifications/CI; B catalog/seller/marketplace; C orders/payments/Docker. Agree module interfaces before dependent work. Feature branches merge into develop, then main, through review. User handles commits and pushes manually unless explicitly authorized otherwise.

Stretch: WebSocket chat, real payment sandbox, monitoring dashboards after core delivery.

