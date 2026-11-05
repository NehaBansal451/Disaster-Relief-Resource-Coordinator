-- V2: adjust the two V1 tables (PROPOSED D2, pending Divay approval).
-- Never edit V1 once applied: Flyway stores its checksum.

-- 1) Allow FIELD_WORKER (needed by API contract). V1 auto-named the constraint users_role_check.
ALTER TABLE users DROP CONSTRAINT users_role_check;
ALTER TABLE users ADD CONSTRAINT ck_users_role CHECK (role IN
  ('ADMIN','OFFICER','WAREHOUSE_MANAGER','FIELD_WORKER','NGO','VOLUNTEER','CITIZEN'));

-- 2) Email must be unique ignoring upper/lower case (V1 treated A@x.com and a@x.com as different).
ALTER TABLE users DROP CONSTRAINT users_email_key;
CREATE UNIQUE INDEX uq_users_email_lower ON users (lower(email));

-- 3) Language for the multilingual UI (English, Hindi, Punjabi) + demo-data flag.
ALTER TABLE users
  ADD COLUMN preferred_language VARCHAR(5) NOT NULL DEFAULT 'en',
  ADD COLUMN is_demo            BOOLEAN    NOT NULL DEFAULT FALSE,
  ADD CONSTRAINT ck_users_language CHECK (preferred_language IN ('en','hi','pa'));

ALTER TABLE organizations
  ADD COLUMN contact_email VARCHAR(255),
  ADD COLUMN contact_phone VARCHAR(30),
  ADD COLUMN is_demo       BOOLEAN NOT NULL DEFAULT FALSE;

-- 4) Foreign keys need an index so joins/deletes do not scan the whole table.
CREATE INDEX idx_users_organization_id ON users (organization_id);
