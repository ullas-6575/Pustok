# Database guide

Supabase is the PostgreSQL provider. The application connects using JDBC through the session pooler on port 5432. Supabase API keys are not used for this connection.

| Variable | Meaning |
| --- | --- |
| DB_URL | JDBC connection URL including sslmode=require |
| DB_USERNAME | Pooler username, usually postgres.PROJECT_REF |
| DB_PASSWORD | Database password |
| PORT | Application HTTP port, default 8081 |

## Existing table

public.identity_users contains id, name, email (unique), password_hash, role and created_at. Role is currently a single BUYER/SELLER/ADMIN string. Other tables in the original plan are not implemented.

View users in Table Editor → public → identity_users, not Authentication → Users.

Read-only SQL:

```sql
SELECT id, name, email, role, created_at
FROM public.identity_users
ORDER BY id;
```

## Migrations

Files: src/main/resources/db/migration. V1 creates identity_users. Add V2, V3 etc. for new changes. Do not modify applied migrations or delete history to hide checksum errors. Hibernate ddl-auto=validate checks mapping compatibility.

The existing non-empty Supabase schema was baselined at version 0, then V1 was applied. Current application.properties enables automatic baselining. Before new environments or release, make bootstrap explicit and inspect the target schema first.

Use isolated databases for tests. Review Data API exposure and table grants for identity data before public deployment.

## Planned relationships

User → seller profile; seller → books; category → books; buyer → cart/orders; cart/order → items; order → payment; users → resale/exchange/notifications. Define constraints and lifecycle rules before writing each migration.
