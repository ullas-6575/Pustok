# Troubleshooting

| Symptom | Action |
| --- | --- |
| Connection refused | Read startup console, wait for Tomcat started, confirm port 8081 or PORT override. |
| Port occupied | Stop the matching project instance or choose another PORT. |
| Database authentication failed | Check database password and pooler username privately; API keys are different. |
| .env not loaded | Set working directory to project root; inspect environment overrides without exposing secrets. |
| Users missing in Supabase Auth | Open Table Editor → public.identity_users. |
| Non-empty schema without history | Inspect schema before intentional Flyway baselining; this project was bootstrapped at version 0. |
| Migration checksum mismatch | Restore applied migration and create a new migration for changes. |
| Anonymous root returns 403 | Open /signin; login redirect improvement is pending. |
| Login lost after restart | Sessions are currently server-local; sign in again. |
| Books never change | Current landing page contains static demo content. |

## Windows wrapper

This workspace previously reported “Cannot index into a null array” / “Cannot start maven from wrapper”. Root cause remains unresolved; a cached Maven executable worked directly.

Use IntelliJ Maven integration or an installed Maven with `mvn spring-boot:run`. Do not copy another user's absolute cache path. Repair and verify the wrapper before declaring it fixed. Successful compilation does not prove startup: migrations execute during application startup.

