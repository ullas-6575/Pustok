-- Accounts can be disabled by an admin. Existing rows stay enabled.
ALTER TABLE identity_users ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;
