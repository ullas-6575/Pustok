# Testing and verification

Historical checks confirmed compilation, startup, signup HTTP 200 and two saved users. Comprehensive security and business coverage has not been established. The generated context test is not a complete test suite.

## Commands

After configuring an isolated test database:

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
```

Use Testcontainers or a dedicated PostgreSQL service for integration tests. Do not run destructive tests against the shared Supabase project.

## Essential checks

Authentication: valid registration, duplicate and concurrent duplicate emails, validation, UTF-8 password limits, valid/invalid login, session ID rotation, CSRF rejection, logout invalidation and protected route access. Ensure passwords never appear in logs or rendered form values.

Business modules: seller approval/ownership, stock races, cart ownership, totals, checkout rollback, state transitions, payment idempotency and exchange conflicts.

## Release walkthrough

1. Start app and open signup/signin.
2. Register a disposable account, log in, view home, log out.
3. Confirm protected content is inaccessible after logout.
4. Once implemented, run seller approval → book listing → buyer checkout → payment → notification.
5. Verify another buyer cannot access the order.
6. Record reports, environment, date and remaining limitations. Never invent passing test counts.
