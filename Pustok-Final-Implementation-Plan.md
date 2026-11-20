# Pustok: Implementation Plan (aligned to the teacher's requirements)

Pustok is an online book marketplace (new books, resell listings, exchange offers). It is built as one Spring Boot app organized into modules (modular monolith).

You will commit and push manually, so this plan gives the order of work, branches, merge points, and what must pass before every merge. It does not include any publishing steps.

---

## 1. Scope decisions

The teacher's rubric rewards security, tests, Docker, CI/CD, deployment and relationships. It does not reward extra features. So:

**Must build (core):** auth, roles, books, categories, seller profile, cart, orders, payments, resell and exchange, notifications.
**Stretch (only if everything else is green):** WebSocket chat, SSLCommerz sandbox.

Roles must be **ADMIN, SELLER, BUYER** (the earlier CUSTOMER and MERCHANT names are replaced).

### Tables (13, requirement is 6 or more)
| Table | Relationship |
|---|---|
| `identity_users` | M:M with roles |
| `identity_roles` | |
| `identity_user_roles` | join table (M:M) |
| `identity_revoked_tokens` | logout denylist |
| `catalog_categories` | 1:M with books |
| `catalog_books` | M:1 category, M:1 seller |
| `seller_profiles` | 1:1 with user, 1:M with books |
| `orders_carts`, `orders_cart_items` | 1:1 user, 1:M items |
| `orders_orders`, `orders_order_items` | 1:M, M:1 book |
| `payment_payments` | 1:1 with order |
| `marketplace_resell_listings` | M:1 user, M:1 book |
| `marketplace_exchange_offers` | M:1 listing, M:1 offerer |
| `notification_notifications` | M:1 user |

### Controllers (10, requirement is 5 or more)
`AuthController`, `UserAdminController`, `CategoryController`, `BookController`, `SellerController`, `CartController`, `OrderController`, `PaymentController`, `ResellController`, `ExchangeController`, plus `NotificationController` (GET only).

**Full CRUD entities (requirement is 3):** Book, Category, ResellListing (also Seller profile).

### Authentication details
- Register, login (returns JWT), logout (token id stored in `identity_revoked_tokens`, checked by the JWT filter), BCrypt hashing.
- URL rules in `SecurityConfig` plus `@PreAuthorize` on service or controller methods.

| Endpoint group | Access |
|---|---|
| `POST /api/auth/register`, `/login` | public |
| `GET /api/books/**`, `/api/categories/**` | public read |
| Book and category write | SELLER (own books), ADMIN |
| Seller approval, user management | ADMIN |
| Cart, orders, payments | BUYER |
| Order status update (ship) | SELLER owner or ADMIN |
| Resell and exchange | BUYER, SELLER |

---

## 2. Architecture and design patterns (needed to avoid automatic failure)

Package layout: `com.pustok.<module>.api` (public interfaces, DTOs, events) and `com.pustok.<module>.internal` (private). Modules: `common`, `identity`, `catalog`, `seller`, `marketplace`, `order`, `payment`, `notification`. ArchUnit test enforces that modules only use each other's `api`.

| Pattern | Where | Owner |
|---|---|---|
| Strategy | `CommissionStrategy` (category, flat), `PaymentGateway` (Mock, Bkash, Nagad, COD) | B, C |
| Factory | `PaymentGatewayFactory` | C |
| State | `OrderState`, `ExchangeState` | C, B |
| Observer | `OrderPlacedEvent`, `ExchangeAcceptedEvent`, `PaymentCompletedEvent` handled by notification | all |
| Template Method | `AbstractNotificationSender` (Email, Log) | A |
| Builder | DTO and test-data builders (Lombok `@Builder`) | all |
| Facade | module `api` services | all |
| Repository | Spring Data JPA | all |

Document each in the README with a file path.

---

## 3. Team split

| Member | Modules | Extra |
|---|---|---|
| **A** (Lead) | identity, common, notification | CI workflow, branch protection, Render deploy, final README, reviews |
| **B** | catalog, seller, marketplace | ArchUnit, OpenAPI docs, ER diagram |
| **C** | order, payment | Dockerfile, docker-compose, Actuator, Prometheus, Grafana |

Each member reviews the other two members' PRs.

---

## 4. Git workflow (matches the teacher's rule)

```
main      protected, only receives PRs from develop, needs 1 approval, CI must pass
develop   integration branch, receives PRs from feature branches (1 approval)
feature/* one branch per feature, e.g. feature/b-catalog-crud
```

Note: a feature is merged into `develop` first, not straight into `main`. `main` is updated only by a PR from `develop` at the end of each week (milestone). The Lead presses the merge button, but only after another member has approved. No direct pushes to `main` or `develop` from anyone, including the Lead.

Branch protection (GitHub, Settings, Branches), for `main` and `develop`:
- Require a pull request before merging, 1 approval
- Require status checks to pass (the `test` job)
- Block force pushes, and apply to administrators too

Use a public repo, because branch protection on private repos needs a paid plan.

### Definition of done before any feature merge
1. The feature's unit tests pass and the integration tests pass (`./mvnw verify` is green).
2. ArchUnit passes.
3. Rebased on latest `develop` (`git fetch && git rebase origin/develop`).
4. PR opened with description, another member approves, CI is green.
5. Merged with a merge commit (so the history shows each feature).

Commit style: `feat(catalog): add Book entity`, `test(order): ...`, `fix(...)`, `ci: ...`, `docs: ...`. Commit small and often.

---

## 5. Week-by-week plan with merge order

### Week 1: Foundation (merge target: `develop`, then `main` at end of week)

| # | Branch | Owner | Content | Tests |
|---|---|---|---|---|
| M1 | `feature/a-project-setup` | A | Spring Boot 3 / Java 21 project, `common` (exceptions, base event), global `@RestControllerAdvice`, `application.yml` using env vars, `.env.example`, `.gitignore`, README skeleton | |
| M2 | `feature/a-ci-pipeline` | A | `.github/workflows/ci.yml` (build and test on PR), PR template | CI green |
| M3 | `feature/c-docker` | C | Dockerfile (multi-stage), `docker-compose.yml` (app + postgres + healthcheck), `.env` usage | `docker compose up --build` works |
| M4 | `feature/a-identity-auth` | A | User, Role, register, login, BCrypt, JWT, JwtFilter, SecurityConfig, role seeding (ADMIN, SELLER, BUYER) | 4 unit, 2 integration |
| M5 | `feature/b-catalog-crud` | B | Category and Book entities, CRUD, validation DTOs, `CatalogApi` | 4 unit, 1 integration |
| M6 | `feature/c-test-base` | C | Testcontainers base class, test data builders | |
| M7 | `feature/b-archunit` | B | ArchUnit rules | 1 arch test |

Day 1 (all together, 1 hour): agree on `api` interfaces and event records, commit stubs in M1.
At end of week: PR `develop` to `main`. Milestone: login returns a JWT, `docker compose up --build` works, CI green.

### Week 2: Core business

| # | Branch | Owner | Content | Tests |
|---|---|---|---|---|
| M8 | `feature/a-rbac-logout` | A | `@PreAuthorize` everywhere, logout with token denylist, admin user management | 3 unit, 2 integration (401, 403) |
| M9 | `feature/b-seller-profile` | B | Seller registration, admin approval, `SellerApi`, only approved sellers can list books | 3 unit, 1 integration |
| M10 | `feature/b-stock-locking` | B | `@Version` optimistic lock, `reserveStock` and `releaseStock` in `CatalogApi`, `CommissionStrategy` | 4 unit |
| M11 | `feature/c-cart` | C | Cart and CartItem CRUD (BUYER only) | 2 unit, 1 integration |
| M12 | `feature/c-order-checkout` | C | Order, OrderItem, `OrderState`, checkout reserves stock via `CatalogApi`, publishes `OrderPlacedEvent` | 5 unit, 1 integration |

Milestone: seller lists a book, buyer adds to cart and checks out, stock decreases. PR `develop` to `main`.

### Week 3: Payments, marketplace, notifications, observability

| # | Branch | Owner | Content | Tests |
|---|---|---|---|---|
| M13 | `feature/c-payment` | C | `PaymentGateway` strategies, `PaymentGatewayFactory`, idempotency key, order moves to PAID | 4 unit, 1 integration |
| M14 | `feature/b-resell-exchange` | B | ResellListing CRUD, ExchangeOffer and `ExchangeState`, commission on resale, `ExchangeAcceptedEvent` | 4 unit, 1 integration |
| M15 | `feature/a-notification` | A | `AbstractNotificationSender` (Template Method), listeners for the three events, `GET /api/notifications` | 3 unit, 1 integration |
| M16 | `feature/c-observability` | C | Actuator, `micrometer-registry-prometheus`, Prometheus and Grafana in `docker-compose` (provisioned dashboard JSON) | |
| M17 | `feature/b-openapi` | B | springdoc OpenAPI, ER diagram image | |

Milestone: full flow works (list, buy, pay, notify), plus resell and exchange. PR `develop` to `main`.

### Week 4: Deployment, tests gap, documentation

| # | Branch | Owner | Content |
|---|---|---|---|
| M18 | `feature/a-deploy-render` | A | Render web service (Docker) and PostgreSQL, deploy job in `ci.yml`, secrets, env vars on Render |
| M19 | `feature/b-test-gaps` | B | Fill missing tests until the totals below are reached, JaCoCo report |
| M20 | `feature/c-test-gaps` | C | Same for order and payment, security checks, cleanup |
| M21 | `feature/a-readme` | A | Final README (all sections in section 8) |
| M22 | `feature/*-fixes` | all | Bug fixes found in rehearsal |

Final: PR `develop` to `main`, auto-deploy runs, check the live URL. Rehearse the demo twice.

---

## 6. Test targets (requirement: 20 unit, 10 integration)

Aim higher than the minimum, in case some tests are judged weak.

| Member | Unit (Mockito) | Integration (SpringBootTest + MockMvc) |
|---|---|---|
| A | 9: register, duplicate email, login ok, bad password, JWT valid, JWT expired, logout denylist, role assign, notification sender | 5: register 201, login returns token, no token 401, wrong role 403, notifications list |
| B | 11: book create, update, delete, not found, stock reserve, insufficient stock, 2 commission strategies, resell create, exchange transitions valid and invalid | 4: seller CRUD book, buyer forbidden, category CRUD, admin approves seller |
| C | 11: cart add, cart empty checkout, checkout ok, 3 order-state transitions, factory selects Mock, Bkash, COD, idempotency | 4: cart to checkout, seller ships order, payment flow, buyer cannot read another buyer's order |
| **Total** | **31** | **13** |

Unit tests test the service layer with mocks. Integration tests use real PostgreSQL via Testcontainers (CI runners have Docker) or a Postgres service container.

---

## 7. Key technical snippets

### Dockerfile
```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
```

### docker-compose.yml (outline)
```yaml
services:
  db:
    image: postgres:16
    environment:
      POSTGRES_DB: ${DB_NAME}
      POSTGRES_USER: ${DB_USER}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USER} -d ${DB_NAME}"]
      interval: 5s
      retries: 10
  app:
    build: .
    depends_on:
      db: { condition: service_healthy }
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/${DB_NAME}
      SPRING_DATASOURCE_USERNAME: ${DB_USER}
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
    ports: ["8080:8080"]
  prometheus:   # bonus
    image: prom/prometheus
    volumes: ["./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml"]
    ports: ["9090:9090"]
  grafana:      # bonus
    image: grafana/grafana
    ports: ["3000:3000"]
```
Commit `.env.example` only. `.env` stays in `.gitignore`. No credentials in any committed file.

### GitHub Actions `ci.yml`
```yaml
name: CI
on:
  pull_request:
    branches: [develop, main]
  push:
    branches: [develop, main]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 21, cache: maven }
      - run: ./mvnw -B verify
  deploy:
    needs: test
    if: github.ref == 'refs/heads/main' && github.event_name == 'push'
    runs-on: ubuntu-latest
    steps:
      - run: curl -fsS -X POST "${{ secrets.RENDER_DEPLOY_HOOK_URL }}"
```
Name the test job `test` so branch protection can require it.

### Render setup
1. Create a PostgreSQL instance on Render, and a Web Service from the repo using the Dockerfile.
2. Turn off Render's own auto-deploy, and use the Deploy Hook URL from the service settings. Store it as the GitHub secret `RENDER_DEPLOY_HOOK_URL`. That makes GitHub Actions the thing that deploys, which is what the teacher asks for.
3. Set env vars on Render: `SPRING_DATASOURCE_URL` (in `jdbc:postgresql://host:5432/dbname` form, built from Render's internal host, because Render's own connection string is not in JDBC format), `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`, `PORT` handling via `server.port=${PORT:8080}`.
4. Free Render services sleep when idle and the free database has a limited lifetime, so open the app before the demo to wake it, and check the database expiry date.

---

## 8. README contents (5 marks, plus needed for submission)
- Project description and roles
- Architecture diagram (modules and `api` boundaries)
- ER diagram
- API endpoint table (method, path, role)
- Design pattern table with file paths
- Run instructions (`cp .env.example .env`, `docker compose up --build`)
- Test instructions and counts
- CI/CD explanation (branch strategy, workflow, Render deploy)
- Monitoring (Actuator, Prometheus, Grafana URLs)
- Live URL and team contributions

---

## 9. Rubric and failure-condition checklist

| Item | How this plan covers it | Done |
|---|---|---|
| No design patterns | Strategy, Factory, State, Observer, Template Method, Builder | [ ] |
| No RBAC | ADMIN, SELLER, BUYER with URL rules and `@PreAuthorize`, tested for 401 and 403 | [ ] |
| Direct push to main | Branch protection, PR plus 1 approval, apply to admins | [ ] |
| No Docker | Dockerfile and compose, works with `docker compose up --build` | [ ] |
| Tests missing | 31 unit and 13 integration, run in CI | [ ] |
| Not deployed | Render, deploy from `main` through Actions, live URL in README | [ ] |
| Bonus | Actuator, Prometheus, Grafana dashboard | [ ] |

## 10. Demo script (10 minutes)
1. Show the repo: branches, protection settings, merged PRs with approvals.
2. Show a PR with a failing then passing CI run (make one intentionally on a throwaway branch earlier and keep the history).
3. Merge `develop` to `main`, show the deploy job succeeding.
4. Open the live URL: register as ADMIN, SELLER, BUYER, show a 403 for a wrong role.
5. Run the flow: seller lists a book, buyer orders and pays, stock drops, notification appears.
6. Show `/actuator/prometheus`, then the Grafana dashboard.
7. Show the test report and the README.
