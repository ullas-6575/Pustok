# Roles and first administrator

Public signup always creates BUYER, even if JSON includes a role field. Authentication loads current roles from the database on every request, so role changes apply to existing JWTs immediately.

| Route | Required role |
| --- | --- |
| /admin, /admin/**, /api/admin/** | ADMIN |
| /seller, /seller/**, /api/seller/** | SELLER |
| /buyer, /buyer/**, /api/buyer/** | BUYER |
| / | Any authenticated account |

Roles are exclusive: ADMIN does not automatically inherit BUYER or SELLER. AdminService also enforces ADMIN using method security. Seller and buyer pages are access-controlled placeholders; catalog/cart functionality is pending.

## Create the first admin

Edit your ignored .env using an email that has never signed up:

```properties
ADMIN_BOOTSTRAP_ENABLED=true
ADMIN_EMAIL=your-new-admin@example.com
ADMIN_PASSWORD=YOUR_STRONG_PASSWORD
```

Use at least 12 password characters and no more than 72 UTF-8 bytes. Start/restart the app with the project root as working directory. Bootstrap creates one ADMIN and hashes the password. It refuses to promote an existing non-admin account and never resets an existing admin password.

After successful startup, set ADMIN_BOOTSTRAP_ENABLED=false and remove ADMIN_PASSWORD from .env. Sign in at /signin with that admin account and open /admin. Bootstrap is disabled by default; no default admin credentials are generated.

## Manage access

The admin page lists 50 users per page and lets the administrator change non-admin users between BUYER and SELLER. Public registration cannot select privileged roles. Admin accounts cannot be promoted/demoted through this page/API. This is role management; seller applications/profile approval are a future feature.

API: GET /api/admin/users?page=0 and PATCH /api/admin/users/{id}/role with JSON {"role":"SELLER"} or {"role":"BUYER"}. Supply an admin Bearer JWT, or admin cookie plus CSRF. Missing credentials give API 401; the wrong role gives 403. Existing admin targets and ADMIN role assignments are rejected.

## Verification

JwtServiceTest covers claims, expiry, tampering and revocation. JwtAccessTest exercises the real SecurityFilterChain with MockMvc and mocked persistence: login, cookie/Bearer access, CSRF, logout revocation, role boundaries, role changes and public signup role injection. AdminBootstrapTest verifies opt-in creation, password hashing and refusal to promote existing buyers. These tests do not write to Supabase.
