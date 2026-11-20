# Deployment and final handoff

Docker, compose and CI/CD are planned. Their files do not exist yet.

## Release sequence

1. Complete authentication hardening, business flows and isolated tests.
2. Add a multi-stage Dockerfile and compose with isolated PostgreSQL.
3. Configure DB_URL, DB_USERNAME, DB_PASSWORD and PORT as deployment secrets/settings.
4. Add PR CI for compilation, tests and architecture checks; enforce reviewed merges.
5. Configure HTTPS, secure session cookies and appropriate proxy settings.
6. Review database privileges, backups and explicit Flyway bootstrap behavior.
7. Deploy the intended release branch, verify migrations, login/logout and health checks.
8. Record live URL and rollback instructions in README.

JWT authentication survives restarts when the signing key stays the same. CSRF/flash state may use a session; multiple instances still need a plan for that state.

## Final deliverables

- Live URL and reproducible setup instructions.
- Team contributions and reviewed merge history.
- ER diagram, actual endpoint documentation and design pattern source links.
- Measured unit/integration test reports.
- Demo rehearsal: seller approval → list → checkout → pay → notify.
- Controlled admin provisioning: public signup must stay BUYER-only.
- Prior build and a forward-compatible migration/recovery plan.

The original plan suggests Render, but hosting is not configured. Confirm provider details when deployment work begins.

