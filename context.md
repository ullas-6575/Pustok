# Project context

Snapshot: 2026-11-20. Update after each milestone and inspect code before relying on this snapshot.

## Goal

Deliver a marketplace for new books, resale and exchange, with buyer checkout, approved sellers and admin oversight. Confirm requirements from the actual academic rubric; the original plan is a proposal.

## Implemented

Base package: com.example.EPustok. EPustokApplication starts the app. identity/AuthController handles browser registration and login; SecurityConfig supplies authentication and logout filtering. UserRepository persists User to identity_users. HomeController renders a static home page. Thymeleaf templates are in resources/templates; CSS in resources/static/css. V1 creates the user table.

Supabase supplies PostgreSQL storage. Spring Security owns authentication. Login now uses signed JWTs in an HttpOnly cookie or API Bearer header. Signup assigns BUYER. The entity has one role string; multi-role tables are not implemented.

Historical session evidence: successful startup on 8081, signup HTTP 200 and two stored user records. These observations do not guarantee the current process or database state.

## Known gaps / next work

- Manual signin saves the security context without an explicit session fixation/session authentication strategy.
- Anonymous root requests currently return 403 instead of redirecting to signin.
- Duplicate registration pre-check does not handle concurrent unique constraint violations.
- Password character validation does not enforce BCrypt's UTF-8 byte limit.
- Logout ownership is duplicated between controller and security filter.
- SecurityConfig permits obsolete /api/auth routes with no handlers.
- Automatic Flyway baselining should become an explicit bootstrap decision.
- No service layer, role administration, shopping functionality or comprehensive test suite yet.

Start with authentication hardening, then seller/catalog, then checkout. Update roadmap and architecture as implementation changes. Do not describe planned features as completed.


JWT update: JwtService, JwtFilter, AuthApiController and V2 revocation migration implement cookie/Bearer login. The manual session-login, anonymous redirect, duplicate logout and obsolete API gaps above are superseded. Other registration hardening items remain.
