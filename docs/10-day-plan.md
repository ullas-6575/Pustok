# Pustok: 10-day plan to final delivery

Written 2026-11-22 against the code at commit `8bfb55f` plus uncommitted work in the tree.
Measured, not assumed: every "done" below was checked against source, migrations or a test run.

Execution model: one day = one session with a Haiku subagent. You commit and push; the subagent does not.

---

## 1. Where the project actually stands

### Built and working

| Area | Evidence |
|---|---|
| Signup, signin, BCrypt, JWT cookie + Bearer, logout denylist | `identity/JwtService`, `JwtFilter`, `V2__jwt_revocation.sql` |
| Roles BUYER / SELLER / ADMIN, admin bootstrap, role administration | `identity/Role`, `AdminService`, `AdminBootstrap` |
| Seller application and admin approval | `market/SellerProfile`, `MarketService`, `V3__seller_catalog.sql` |
| Category CRUD, book CRUD with ownership checks | `market/CatalogController`, `MarketService` |
| Persistent cart with stock and own-book checks | `cart/CartService`, `V4__shopping_cart.sql` |
| COD checkout: server-side totals, price snapshots, stock decrement, request-id idempotency | `order/CheckoutService`, `V5__checkout_orders.sql` |
| Buyer order history and detail, admin order tracking, per-seller shipments | `order/OrderTracking`, `ShipmentService`, `V6__seller_shipments.sql` |
| Resell listings | `market/ResellListing`, `V7`, `V8` |
| Exchange chat over WebSocket (a *stretch* item, already built) | `chat/`, `V9__exchange_chat.sql` |
| 13 tables across 10 migrations (requirement is 6+) | `src/main/resources/db/migration/` |
| PR-based history, 6 merged PRs, no direct main pushes visible | `git log` |

Stack as built: Spring Boot 4.1.2, Java release 17 (local JDK is 27), Spring Security, JPA, Flyway, Thymeleaf, Supabase PostgreSQL. The original plan's "Boot 3 / Java 21 / `com.pustok`" is **not** what exists; the base package is `com.example.EPustok` and stays that way.

### Measured test baseline

`./mvnw -B test` on 2026-11-22: **Tests run 53, Failures 0, Errors 2 — BUILD FAILURE.**

- 51 tests pass. They are unit tests (plain JUnit, Mockito mocks, some standalone MockMvc).
- 2 errors: `EPustokApplicationTests` and `CheckoutServiceTest`. Both are `@SpringBootTest` and fail to load the context because `spring.datasource.password=${DB_PASSWORD}` has no value — there is no `.env` and no test datasource.
- Working integration tests against a real database: **0**.
- Target from the original plan: 31 unit, 13 integration. No JaCoCo.

### Missing, in rubric-risk order

The original plan lists these as automatic-failure conditions:

1. **Design patterns — none of the six exist.** No Strategy, Factory, State, Observer or Template Method anywhere. This is the single biggest risk.
2. **No Docker.** No `Dockerfile`, no compose file. Docker 29.9.1 is installed but the daemon is not running.
3. **No CI/CD.** No `.github/` at all.
4. **Not deployed.** No live URL.
5. **Tests short of target**, and the build is currently red.
6. **Payment module absent.** No `payment_payments` table, no gateway, no payment-level idempotency. Checkout is COD-only and marks nothing as paid.
7. **Notification module absent.**
8. **Exchange offers absent.** Chat exists, but there is no offer entity or accept/reject lifecycle.
9. No ArchUnit, no OpenAPI, no ER diagram.
10. README, `context.md` and `docs/roadmap.md` understate progress — they still call cart, checkout, resell and exchange "planned".

### Uncommitted work in the tree right now

A stopped subagent left 13 modified and 7 new files. It compiles and its 23 new tests pass. It contains:

- `identity/RegistrationService` + tests (registration rules moved out of the controller)
- catalog paging and a `book.html` detail page + tests
- `market/MarketSecurityTest`, `identity/AuthFlowTest`
- buyer order cancellation: `V10__order_cancellation.sql`, `OrderTracking.cancel`, controller route, template button

Known gaps in it: `OrderTracking.cancel` has **no test**, and `V10` has never been applied to any database. Day 1 resolves this.

---

## 2. Standing rules for every subagent session

Paste this block at the top of every daily prompt.

```
Project: /Users/woaliuhasan/Documents/3-2/software lab /Final Project
Read docs/10-day-plan.md, then README.md, context.md, architecture.md before editing.

RULES
- Do NOT run git commit, push, merge, branch, checkout or reset. Leave work uncommitted.
- Never create or print .env or real credentials. Tests must not need live Supabase.
- Keep the base package com.example.EPustok. Do not rename packages.
- Follow the existing style of the module you are editing.
- Add a test for every new behavior. Run ./mvnw -B test and report real counts.
- If something cannot be done locally, report it as BLOCKED with the reason. Never fake it.
- Update docs/roadmap.md, context.md and README.md only for what you actually finished.
- Final message under 60 lines: per-task DONE/PARTIAL/BLOCKED, files changed,
  exact test command and counts, decisions you made, anything you suspect is wrong.
```

After each day you run: `git switch -c <branch>`, review the diff, commit, push, open a PR into `develop`.

---

## 3. The ten days

### Day 1 — Green build, unit/integration split, finish the cancellation work
**Why first:** every later day and all of CI depends on `./mvnw` being green without a database.

- Add `maven-failsafe-plugin`. Surefire keeps `*Test` (no database); failsafe runs `*IT` (database).
- Move the two context-dependent tests to `*IT`: `EPustokApplicationTests` → `ApplicationContextIT`, `CheckoutServiceTest` → keep the mock-based part as `*Test`, move the DB part to `CheckoutServiceIT`. Day 2 gives the ITs a real database.
- Add `src/test/resources/application-test.properties` so no test reads `.env`.
- Finish the cancellation work already in the tree: add unit tests for `OrderTracking.cancel` covering success, a non-PLACED order (409), a shipment already processing (409), and another buyer's order (404).
- Fix anything the review of that WIP turns up.

**Accept:** `./mvnw -B test` exits 0 with 0 failures and 0 errors. `./mvnw -B verify` may still skip ITs.
**Branch:** `feature/a-test-baseline` · `test: split unit and integration tests, green build`

### Day 2 — Docker and a real integration-test database
**You must start Docker Desktop before this session.** The daemon is not running; without it, both halves of this day are blocked.

- Multi-stage `Dockerfile` (build on `maven:3.9-eclipse-temurin-17`, run on a 17 JRE — not the plan's 21, match `pom.xml`).
- `docker-compose.yml`: app + `postgres:16` with a healthcheck, env from `.env`, `.env.example` updated with the local-Postgres variant.
- Testcontainers: add the dependency, write `support/PostgresIT` base class with `@ServiceConnection`, point the Day 1 ITs at it so Flyway runs all 10 migrations against a throwaway Postgres.
- This is also the first real check that `V10` applies cleanly.

**Accept:** `docker compose up --build` serves the app; `./mvnw -B verify` runs the ITs green against Testcontainers.
**Branch:** `feature/c-docker` · `ci: add Dockerfile, compose and Testcontainers base`

### Day 3 — CI/CD pipeline
- `.github/workflows/ci.yml`: job named exactly `test` (branch protection requires that name), `actions/setup-java` with Temurin 17 and Maven cache, runs `./mvnw -B verify`.
- JaCoCo plugin with a report in `verify`, uploaded as a build artifact.
- PR template under `.github/`.
- **You do by hand on GitHub:** create the `develop` branch; enable branch protection on `main` and `develop` (require a PR, 1 approval, require the `test` check, block force pushes, apply to administrators). Keep the repo public so protection is free.

**Accept:** a PR shows the `test` check running and green. Protection settings are screenshot-ready for the demo.
**Branch:** `feature/a-ci-pipeline` · `ci: add GitHub Actions test workflow and JaCoCo`

### Day 4 — Payment module: Strategy + Factory
First of the pattern days. Delivers two of the six patterns.

- `V11__payments.sql`: `payment_payments`, 1:1 with `purchase_order`, unique `idempotency_key`, status `PENDING/COMPLETED/FAILED`, gateway name, amount `NUMERIC(12,2)`.
- `payment/PaymentGateway` interface — **Strategy**. Implementations: `CodGateway`, `MockGateway`, `BkashGateway` (sandbox-shaped, no real network call).
- `payment/PaymentGatewayFactory` — **Factory**, selects by gateway name, rejects unknown names.
- `payment/PaymentService`: charge with an idempotency key, replay returns the first result and never double-charges. `PaymentController` for BUYER.
- Tests: factory picks each of the three gateways, unknown gateway rejected, idempotent replay, failed payment leaves the order unpaid. ~6 unit tests.

**Accept:** paying an order twice with the same key creates one payment row.
**Branch:** `feature/c-payment` · `feat(payment): add gateway strategy, factory and idempotent charging`

### Day 5 — Order state machine (State) + commission (Strategy)
- `order/OrderState`: replace the loose `Status` enum with explicit allowed transitions (`PLACED → PROCESSING → SHIPPED → DELIVERED`, `PLACED → CANCELLED`), rejecting skips and backward moves. Point `OrderTracking.advance`, `ShipmentService` and `cancel` at it. Keep the existing DB check constraints in step.
- Wire payment completion to the order: a completed payment moves the order to PAID/PROCESSING.
- `market/CommissionStrategy` — **Strategy**: `FlatCommission` and `CategoryCommission`, applied to resale listings.
- Tests: ~5 valid and invalid transitions, 2 commission strategies.

**Accept:** no code path can move an order backwards or skip a stage; both commission strategies are tested.
**Branch:** `feature/c-order-state` · `feat(order): add order state machine and commission strategies`

### Day 6 — Notifications: Observer + Template Method
- `V12__notifications.sql`: `notification_notifications`, M:1 user.
- Events `OrderPlacedEvent`, `PaymentCompletedEvent`, `ExchangeAcceptedEvent` published through Spring's `ApplicationEventPublisher` — **Observer**. Listeners use `@TransactionalEventListener(phase = AFTER_COMMIT)` so nothing is sent for a rolled-back transaction.
- `notification/AbstractNotificationSender` — **Template Method** — with `LogNotificationSender` and `EmailNotificationSender` (logging stub, no SMTP).
- `GET /notifications` for the owner only.
- Tests: each listener creates a row, a rolled-back checkout creates none, the template method calls its hooks in order, a user cannot read another user's notifications. ~4 unit + 1 IT.

**Accept:** checkout and payment produce notifications only after commit.
**Branch:** `feature/a-notification` · `feat(notification): add event listeners and sender template`

### Day 7 — Exchange offers: State, on top of the existing chat
- `V13__exchange_offers.sql`: `marketplace_exchange_offers`, M:1 listing, M:1 offerer, status `PROPOSED/ACCEPTED/REJECTED/WITHDRAWN`.
- `market/ExchangeState` — **State** — with the legal transitions; accepting one offer rejects the competing offers on that listing and closes the listing, in one transaction.
- Link offers to the existing `exchange_conversation` so an accepted offer has its chat thread.
- Lombok `@Builder` on the new DTOs and a test-data builder — **Builder**, the sixth pattern.
- Tests: ~4 transition tests including the conflict case, 1 IT.

**Accept:** all six required patterns now exist with real file paths. Two buyers cannot both have an accepted offer on one listing.
**Branch:** `feature/b-exchange-offers` · `feat(market): add exchange offers with state transitions`

### Day 8 — Close the test gap and add architecture tests
- Count what exists, then fill to **31 unit and 13 integration**. Record the measured numbers; do not round up.
- Integration tests must cover: register 201, login returns a token, no token 401, wrong role 403, seller CRUD a book, buyer forbidden, category CRUD, admin approves a seller, cart to checkout, seller ships an order, payment flow, a buyer cannot read another buyer's order, notifications list.
- ArchUnit: start with no-cycles between modules and "controllers depend on services, not on `JdbcTemplate` or another module's repository". Note: `identity.UserRepository` is used across modules today, so either introduce a narrow `UserDirectory` lookup in `identity` or record that one exception as a documented deviation. Do not attempt an `api`/`internal` package split this late.
- JaCoCo report committed to the docs.

**Accept:** `./mvnw -B verify` green, with real counts written into `docs/testing.md`.
**Branch:** `feature/b-test-gaps` · `test: reach 31 unit and 13 integration tests, add ArchUnit rules`

### Day 9 — Deploy, plus the observability bonus
**You must decide the host and create the accounts before this session.** Render free tier is what the original plan assumed.

- Render: a PostgreSQL instance and a Docker web service. Turn Render's auto-deploy **off**; copy the Deploy Hook URL into the GitHub secret `RENDER_DEPLOY_HOOK_URL`, and add a `deploy` job to `ci.yml` that runs only on a push to `main`. GitHub Actions does the deploying, which is what the rubric asks for.
- Render env vars: `SPRING_DATASOURCE_URL` in `jdbc:postgresql://host:5432/db` form (Render's own string is not JDBC), username, password, `JWT_SECRET`, and `JWT_COOKIE_SECURE=true` for HTTPS.
- Confirm Flyway runs all migrations on the fresh database, then create an admin via the bootstrap flag and turn it back off.
- Bonus: add `micrometer-registry-prometheus`, expose the Actuator Prometheus endpoint, add Prometheus and Grafana to compose with a provisioned dashboard.

**Accept:** a live HTTPS URL where signup, login and checkout work; `/actuator/health` is green; a push to `main` triggers the deploy job.
**Branch:** `feature/a-deploy` · `ci: deploy from main via Render deploy hook`

### Day 10 — Documentation, then rehearse
- Rewrite `README.md` with all ten sections the plan's §8 lists: description, architecture diagram, ER diagram, API endpoint table with roles, **design pattern table with real file paths**, run instructions, test instructions with measured counts, CI/CD explanation, monitoring URLs, live URL and team contributions.
- springdoc OpenAPI for the JSON endpoints; generate the ER diagram from the live schema.
- Bring `context.md`, `architecture.md`, `docs/roadmap.md` and `docs/routes.md` in line with what was actually built, and tick the §9 rubric checklist in the original plan.
- Rehearse the 10-minute demo script twice end to end, from a clean browser session.

**Accept:** every row of the §9 checklist is either ticked with evidence or has a written reason.
**Branch:** `feature/a-readme` · `docs: final README, ER diagram, pattern table and rubric checklist`

---

## 4. Things only you can do, with their deadlines

| Needed | For | Deadline |
|---|---|---|
| Start Docker Desktop | Day 2 is fully blocked without it | before Day 2 |
| Create `develop` and set branch protection on GitHub | Day 3, and a rubric failure condition | Day 3 |
| Render (or another host) account and a PostgreSQL instance | Day 9 | before Day 9 |
| `JWT_SECRET` and database credentials for the deployed app | Day 9 | before Day 9 |
| Real teammate commits and approvals | the README contributions section and the "1 approval" rule | throughout |
| The actual rubric from your teacher | this plan trusts `Pustok-Final-Implementation-Plan.md`, which is a proposal | before Day 4 |

## 5. If days run short

Cut in this order, because the rubric rewards none of it: Grafana and Prometheus first, then `EmailNotificationSender` (keep the logging one, the pattern still shows), then OpenAPI, then the ArchUnit cross-module rule. Protect Days 1-3 and Day 9 at all costs — a red build, no Docker, no CI or no deployment each cost more than any feature.

## 6. Risks worth naming

- **Spring Boot 4.1.2 is very new.** Testcontainers, springdoc, ArchUnit and JaCoCo versions may need pinning, and springdoc may not have a Boot 4 release yet. If a library fights back, say so and fall back to a hand-written endpoint table rather than burning a day.
- **`java.version` is 17 while your JDK is 27.** Keep the Dockerfile and CI on 17 to match `pom.xml`; do not "upgrade" it mid-project.
- **The single `role` string column** is not the plan's M:M roles design. You already have 13 tables, so the table count does not need the join table. Changing the role model now would touch authentication, `AdminService` and every `@PreAuthorize`. Recommendation: keep one role, and write the decision down in `architecture.md` instead of leaving it listed as pending.
- **`spring.flyway.baseline-on-migrate=true`** silently baselines a non-empty database. On the fresh Render database it is harmless, but it can hide a missing migration. Day 9 should confirm all migrations actually ran.
