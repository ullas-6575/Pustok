# Pustok

Book marketplace built with Spring Boot, Thymeleaf and Supabase PostgreSQL.

## Current status

Browser signup, signin, JWT logout and a demo home page are implemented. Seller applications, admin approval, category CRUD and seller-owned book CRUD are implemented. Homepage books load from PostgreSQL. Shopping, payments, resale and exchange remain planned.

## Tests

- `./mvnw -B test` runs the unit tests (`*Test`). They need no database.
- Integration tests (`*IT`) run through failsafe in `./mvnw -B verify`. They need a real database, which Day 2 adds, so they are not run yet.

## Run locally

1. Install a compatible JDK. pom.xml targets Java 17; this workspace has run with Java 25.
2. Copy `.env.example` to `.env` only if `.env` does not already exist. Set database credentials.
3. Run `EPustokApplication` in IntelliJ with the project root as working directory, or run `./mvnw spring-boot:run` (Windows: `.\mvnw.cmd spring-boot:run`).
4. Wait for `Started EPustokApplication` and `Tomcat started on port 8081`.

- Signup: http://localhost:8081/signup
- Signin: http://localhost:8081/signin
- Home after login: http://localhost:8081/

PORT overrides 8081. Run one instance per port. See troubleshooting if the Maven wrapper fails.

Users are saved in Supabase **Table Editor → public.identity_users**. Supabase Authentication → Users is separate and is not used.

## Project documentation

| File | Purpose |
| --- | --- |
| [context.md](context.md) | Implementation status, decisions and known gaps |
| [architecture.md](architecture.md) | Current system and target modules |
| [Roadmap](docs/roadmap.md) | Work sequence through final delivery |
| [Development](docs/development.md) | Setup and team conventions |
| [Database](docs/database.md) | Configuration, tables and migrations |
| [Routes](docs/routes.md) | Current browser routes |
| [Testing](docs/testing.md) | Validation and delivery requirements |
| [Troubleshooting](docs/troubleshooting.md) | Startup and login problems |
| [Deployment](docs/deployment.md) | Release preparation |
| [Original plan](Pustok-Final-Implementation-Plan.md) | Original team and rubric proposal |

Current stack: Spring Boot 4.1.2, Java target 17, Spring Security, JPA, Thymeleaf, Flyway and PostgreSQL. The original plan's Boot 3 / Java 21 / JWT design is not the current implementation. Docker, CI/CD and business modules are not implemented. Test coverage has not been established.

Never commit `.env` or real credentials. Commit and push manually using the agreed team workflow.


JWT implementation and API usage: [Authentication guide](docs/authentication.md).

Roles and controlled admin bootstrap are implemented. See [Admin access guide](docs/admin-access.md). Seller applications/approval remain planned.
