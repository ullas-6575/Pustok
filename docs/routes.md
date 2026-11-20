# Implemented browser routes

| Method | Path | Behavior |
| --- | --- | --- |
| GET | /signup | Public registration form |
| POST | /signup | Validates name/email/password, creates BUYER, redirects to signin |
| GET | /signin | Public signin form |
| POST | /signin | Authenticates, issues JWT cookie, redirects to / |
| GET | / | Authenticated demo home |
| POST | /signout | Logout filter invalidates authentication, redirects to signin?logout |
| GET | /css/** | Public stylesheet resources |

POST forms require CSRF tokens; Thymeleaf th:action supplies them. Preserve cookies across requests. These endpoints return HTML/redirects, not JSON.

JSON signup/signin and logout APIs now exist; see authentication.md. Anonymous browser requests redirect to signin. Actuator access/exposure is not finalized.


POST /api/auth/register and /login (aliases /signup and /signin), GET /api/auth/csrf and /me, POST /api/auth/logout are implemented. See authentication.md for CSRF and Bearer usage.
