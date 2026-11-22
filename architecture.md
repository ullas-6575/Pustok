# Architecture

## Current system

Browser → Spring Security → AuthController/HomeController → Thymeleaf HTML.
AuthController → UserRepository → Supabase PostgreSQL.
UserDetailsService → UserRepository authenticates credentials.

The single Spring Boot application renders HTML. Signup validates input, hashes passwords with BCrypt and persists via JPA. Signin issues a signed JWT; subsequent requests verify it and check the database denylist. Flyway changes the schema; Hibernate validates it.

## Target modular monolith

Keep one deployment. Introduce modules under the existing com.example.EPustok base package. Renaming to the original plan's com.pustok requires updating scanning and tests.

| Module | Responsibility |
| --- | --- |
| identity | Users, authentication, roles |
| seller | Seller profiles and approval |
| catalog | Categories, books, prices, stock |
| order | Carts, checkout and order lifecycle |
| payment | Payment gateways and idempotency |
| marketplace | Resale and exchange offers |
| notification | Notifications from business events |
| common | Shared errors and infrastructure primitives |

Modules expose api contracts and keep implementations in internal packages. Controllers handle input; services own business rules and transactions; repositories persist data. Avoid accessing another module's repository directly.

## Boundaries

Keep CSRF for session-authenticated forms. Enforce roles and resource ownership in services. Never trust client-provided totals, seller approval or account roles. Checkout calculates totals and stores immutable order item prices, with concurrency-safe stock reservation. External payments require idempotency and retry rules. Send notifications after transaction commit.

Repository exists through UserRepository. Planned patterns: Strategy/Factory for payments, State for order/exchange transitions, Observer for business events, Template Method for notification senders. Add real source links when implemented.

## Decisions

**Authentication: stateless JWT, not HTTP sessions.** The website keeps the JWT in an HttpOnly cookie; JSON APIs send it as a Bearer token. Spring Security creates no session for authentication (`SessionCreationPolicy.STATELESS`), so there is no session ID to fix. Session fixation is therefore handled by token rotation: every successful signin invalidates any pre-login HTTP session and revokes the token the request presented before issuing a new one. See docs/authentication.md.

**Authorization: one role per account (`User.role` holds one of BUYER, SELLER, ADMIN).** Reasons:
- Every `@PreAuthorize`, `SecurityConfig` matcher, JWT subject lookup and `AdminService` check reads one role string. Moving to a role set would change all of them at once.
- Seller status is a separate concept: a `SellerProfile` with PENDING, APPROVED or REJECTED status. Approval assigns SELLER; rejection keeps BUYER. The role therefore answers "what may this account do now", and the profile records the application.
- Public signup always creates BUYER. Only an admin can grant ADMIN (see docs/admin-access.md).
- Cost accepted: an account cannot hold two roles at once. An admin who also wants to buy needs a second account. A many-to-many role table is deferred unless the rubric requires it.

Authorization for ownership (a seller editing only their own books, a buyer reading only their own orders) is enforced in services, not by role alone.


JWT details: [Authentication guide](docs/authentication.md). Authentication is stateless; CSRF/flash state may still use an HTTP session.

Roles and controlled admin bootstrap are implemented. See [Admin access guide](docs/admin-access.md). Seller applications/approval remain planned.
