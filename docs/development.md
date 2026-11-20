# Development guide

Read README, context.md and architecture.md before starting a feature. Current pom.xml is the version source of truth; old snippets in the original plan are proposals.

## Local setup

Set IntelliJ's working directory to the project root. Copy .env.example only when .env does not already exist. Set database credentials, then run EPustokApplication or `.\mvnw.cmd spring-boot:run`. Stop the previous instance before rerunning on the same port.

.env is imported as Java properties. Use unquoted values: quotes become part of a password. Backslashes follow Java properties escaping rules. Keep passwords separate from JDBC URLs and never log them.

## Conventions

- Controllers validate input; services own transactions and business rules; repositories persist data.
- Preserve component scanning under com.example.EPustok.
- Normalize emails using Locale.ROOT and enforce uniqueness in the database.
- Use BigDecimal for money and explicit currency/rounding rules.
- Add new Flyway migrations instead of editing applied files.
- Enforce both roles and resource ownership.
- Do not return entities containing password hashes to views or APIs.

## Feature completion

Verify success, validation errors, forbidden access and rollback. Update route documentation, context and roadmap. Document real implementation paths for design patterns. Use feature → develop → main with review and passing CI once CI exists.
