-- V4: resource catalogue and warehouses (PROPOSED D3, pending Divay approval).
-- Stock quantities (inventory) are Day 5 and are NOT created here.

CREATE TABLE resources (
  id            BIGSERIAL PRIMARY KEY,
  name          VARCHAR(150) NOT NULL,
  category      VARCHAR(30)  NOT NULL,
  unit          VARCHAR(20)  NOT NULL,
  perishable    BOOLEAN      NOT NULL DEFAULT FALSE,
  shelf_life_days INTEGER,
  handling_notes TEXT,
  active        BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT ck_resources_category CHECK (category IN
    ('FOOD','WATER','MEDICAL','SHELTER','BEDDING','RESCUE_EQUIPMENT','VEHICLE','OTHER')),
  CONSTRAINT ck_resources_unit_not_blank CHECK (length(btrim(unit)) > 0),
  CONSTRAINT ck_resources_name_not_blank CHECK (length(btrim(name)) > 0),
  -- perishable items must state a shelf life; non-perishable items must not.
  CONSTRAINT ck_resources_shelf_life CHECK (perishable = (shelf_life_days IS NOT NULL)),
  CONSTRAINT ck_resources_shelf_life_positive CHECK (shelf_life_days IS NULL OR shelf_life_days > 0)
);
CREATE UNIQUE INDEX uq_resources_name_lower ON resources (lower(name));
CREATE INDEX idx_resources_category ON resources (category);

CREATE TABLE warehouses (
  id              BIGSERIAL PRIMARY KEY,
  code            VARCHAR(20)  NOT NULL,
  name            VARCHAR(200) NOT NULL,
  organization_id BIGINT,
  manager_user_id BIGINT,
  address         TEXT,
  district        VARCHAR(100),
  state           VARCHAR(100),
  contact_phone   VARCHAR(30),
  capacity_m3     NUMERIC(10,2),
  status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
  location        geography(Point, 4326) NOT NULL,
  is_demo         BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT uq_warehouses_code UNIQUE (code),
  CONSTRAINT fk_warehouses_organization FOREIGN KEY (organization_id) REFERENCES organizations(id),
  CONSTRAINT fk_warehouses_manager FOREIGN KEY (manager_user_id) REFERENCES users(id),
  CONSTRAINT ck_warehouses_status CHECK (status IN ('ACTIVE','INACTIVE')),
  CONSTRAINT ck_warehouses_capacity CHECK (capacity_m3 IS NULL OR capacity_m3 > 0),
  CONSTRAINT ck_warehouses_name_not_blank CHECK (length(btrim(name)) > 0)
);
CREATE INDEX idx_warehouses_status       ON warehouses (status);
CREATE INDEX idx_warehouses_organization ON warehouses (organization_id);
CREATE INDEX idx_warehouses_manager      ON warehouses (manager_user_id);
CREATE INDEX gix_warehouses_location     ON warehouses USING GIST (location);
