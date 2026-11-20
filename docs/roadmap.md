# Roadmap to completion

## Milestone 1: Authentication foundation

- [x] Supabase connection, user migration, browser signup/signin/logout.
- [x] Demo landing page.
- [ ] Fix session fixation protection, anonymous login redirect, concurrent duplicate signup and password byte validation.
- [ ] Consolidate logout handling; remove obsolete API allowlist entries.
- [ ] Move registration business rules into a service.
- [ ] Decide session/JWT and single-role/multi-role requirements.
- [x] Add controlled admin provisioning and authorization tests.

Exit: registration, failed login, session renewal, CSRF rejection and logout work reliably.

## Milestone 2: Seller and catalog

- [ ] Seller application, profile and admin approval.
- [ ] Category CRUD for admins.
- [ ] Book CRUD for approved sellers, with ownership checks.
- [ ] Real catalog, search, pagination and book details.
- [ ] Replace demo home content with real data and empty states.

Exit: approved sellers list books; other sellers cannot modify those listings.

## Milestone 3: Checkout

- [ ] Buyer-owned cart and quantity updates.
- [ ] Server-calculated totals and order item price snapshots.
- [ ] Transactional checkout and concurrency-safe stock reservation.
- [ ] Order history, shipping and cancellation transitions.

Exit: checkout cannot oversell; buyers cannot access others' carts/orders.

## Milestone 4: Payments and marketplace

- [ ] Mock/COD payment strategies and gateway factory.
- [ ] Payment idempotency and retry behavior.
- [ ] Resale listing CRUD and ownership checks.
- [ ] Exchange offer state transitions.
- [ ] Notifications after successful business transaction commits.

Exit: list → buy → pay → notify works without duplicate payments.

## Milestone 5: Delivery

- [ ] Isolated unit/integration tests and architecture checks.
- [ ] Dockerfile and compose with local PostgreSQL.
- [ ] PR CI, reviewed merges and branch protection.
- [ ] Actual API documentation, ER diagram and implemented design pattern links.
- [ ] Deployment, HTTPS, secrets and health checks.
- [ ] Final README, team contributions, test reports and demo rehearsal.

Original plan targets at least 20 unit and 10 integration tests, with 31/13 as the goal. Confirm against the actual rubric; record measured counts.

Original ownership: A identity/common/notifications/CI; B catalog/seller/marketplace; C orders/payments/Docker. Agree module interfaces before dependent work. Feature branches merge into develop, then main, through review. User handles commits and pushes manually unless explicitly authorized otherwise.

Stretch: WebSocket chat, real payment sandbox, monitoring dashboards after core delivery.

