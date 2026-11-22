# JWT authentication

The website now authenticates requests with signed HS256 JWTs. Spring Security does not store login authentication in HTTP sessions. A small session may still exist for CSRF protection and signup flash messages.

## Browser

POST /signin verifies credentials and sets PUSTOK_TOKEN, an HttpOnly, SameSite=Lax cookie with a one-hour lifetime. Every protected request verifies signature, issuer, expiration and token revocation, then loads current user authorities from the database. Anonymous pages redirect to /signin.

POST /signout requires CSRF, persists the token ID in identity_revoked_tokens and clears the cookie. A copied revoked token cannot authenticate again. Expired denylist rows can be removed periodically after their expires_at timestamp.

## JSON API

GET /api/auth/csrf returns headerName and token. Retain its session cookie and send that CSRF header on registration/login requests.

- POST /api/auth/register (alias /signup): JSON name, email, password; creates BUYER.
- POST /api/auth/login (alias /signin): JSON email, password; returns accessToken, tokenType=Bearer, expiresIn=3600.
- GET /api/auth/me: protected account identity.
- POST /api/auth/logout: revokes presented token.

For protected API requests send Authorization: Bearer <accessToken>. Explicit Bearer requests do not require CSRF; cookie-authenticated requests do. API authentication failures return 401.

## Configuration

JWT_SECRET is a Base64-encoded random key of at least 32 bytes. A random local key has been generated in ignored .env. Keep it stable across restarts; rotating it invalidates existing tokens. Never commit the real value.

Set JWT_COOKIE_SECURE=true for HTTPS deployment; false supports localhost HTTP. Cookie path is /. Tokens contain subject, issuer, issued/expiry times and random jti, never passwords. There is no refresh token flow: sign in again after expiration.

V2__jwt_revocation.sql creates the logout denylist. Let Flyway apply it on restart. Unit checks cover valid token claims, signature tampering and revocation. Full browser/API integration should also verify CSRF, expiry, login and logout against an isolated test database.

## Session fixation and renewal

Authentication state is the JWT, not an HTTP session, so there is no authenticated session ID for an attacker to fix. Two measures still apply:

- Signin (`AuthController.signin`, `AuthApiController.login`) invalidates any existing HTTP session, which may hold pre-login state such as the CSRF token or flash messages, and then calls `JwtService.rotate`. Rotation revokes the token the request presented and issues a new one with a fresh `jti`. A stolen pre-login token therefore stops working after the victim signs in.
- Logout runs through `JwtService.logout` only, for both `/signout` and `/api/auth/logout`. It revokes the presented token, clears the cookie and clears the security context.

Covered by `AuthFlowTest.successfulLoginRotatesTokenAndInvalidatesPreLoginSession` and `AuthFlowTest.browserLogoutRevokesTokenAndClearsCookie`.

## Routes

`SecurityConfig` permits these `/api/auth` paths without authentication: `signup`, `register`, `signin`, `login` and `csrf`. Each has a handler in `AuthApiController` (signup and register, signin and login are aliases). `AuthFlowTest.permittedApiAuthPathsAllHaveHandlers` checks that none routes to nothing.

