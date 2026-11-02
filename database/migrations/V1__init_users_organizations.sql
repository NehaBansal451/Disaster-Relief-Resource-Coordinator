CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE organizations (
  id          BIGSERIAL PRIMARY KEY,
  name        VARCHAR(200) NOT NULL UNIQUE,
  type        VARCHAR(30)  NOT NULL CHECK (type IN ('GOVERNMENT','NGO','OTHER')),
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE users (
  id              BIGSERIAL PRIMARY KEY,
  full_name       VARCHAR(150) NOT NULL,
  email           VARCHAR(255) NOT NULL UNIQUE,
  password_hash   VARCHAR(100) NOT NULL,
  role            VARCHAR(30)  NOT NULL
      CHECK (role IN ('ADMIN','OFFICER','WAREHOUSE_MANAGER','NGO','VOLUNTEER','CITIZEN')),
  organization_id BIGINT REFERENCES organizations(id),
  enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_users_role ON users(role);
