-- V6: stock per warehouse, append-only stock ledger, shelters (Day 5, PROPOSED, pending Divay approval).
-- Rule: available = quantity_on_hand - quantity_reserved. Reserved stock is still on hand until dispatched, so it is never counted twice.

CREATE TABLE inventory (
  id               BIGSERIAL PRIMARY KEY,
  warehouse_id     BIGINT      NOT NULL,
  resource_id      BIGINT      NOT NULL,
  batch_ref        VARCHAR(50) NOT NULL DEFAULT 'DEFAULT',
  quantity_on_hand INTEGER     NOT NULL DEFAULT 0,
  quantity_reserved INTEGER    NOT NULL DEFAULT 0,
  expiry_date      DATE,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT fk_inventory_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id) ON DELETE RESTRICT,
  CONSTRAINT fk_inventory_resource  FOREIGN KEY (resource_id)  REFERENCES resources(id)  ON DELETE RESTRICT,
  CONSTRAINT uq_inventory_batch UNIQUE (warehouse_id, resource_id, batch_ref),
  CONSTRAINT ck_inventory_on_hand  CHECK (quantity_on_hand >= 0),
  CONSTRAINT ck_inventory_reserved CHECK (quantity_reserved >= 0),
  -- Last line of defence against over-reservation: the database refuses it even if application code is wrong.
  CONSTRAINT ck_inventory_reserved_le_on_hand CHECK (quantity_reserved <= quantity_on_hand),
  CONSTRAINT ck_inventory_batch_not_blank CHECK (length(btrim(batch_ref)) > 0)
);
CREATE INDEX idx_inventory_resource ON inventory (resource_id);
CREATE INDEX idx_inventory_warehouse ON inventory (warehouse_id);
CREATE INDEX idx_inventory_expiry ON inventory (expiry_date) WHERE expiry_date IS NOT NULL;

CREATE TABLE inventory_transactions (
  id             BIGSERIAL PRIMARY KEY,
  inventory_id   BIGINT      NOT NULL,
  warehouse_id   BIGINT      NOT NULL,
  resource_id    BIGINT      NOT NULL,
  type           VARCHAR(20) NOT NULL,
  quantity       INTEGER     NOT NULL,
  on_hand_after  INTEGER     NOT NULL,
  reserved_after INTEGER     NOT NULL,
  reference_type VARCHAR(30),
  reference_id   BIGINT,
  note           VARCHAR(500),
  actor_user_id  BIGINT      NOT NULL,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT fk_inv_tx_inventory FOREIGN KEY (inventory_id) REFERENCES inventory(id) ON DELETE RESTRICT,
  CONSTRAINT fk_inv_tx_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id) ON DELETE RESTRICT,
  CONSTRAINT fk_inv_tx_resource  FOREIGN KEY (resource_id)  REFERENCES resources(id)  ON DELETE RESTRICT,
  CONSTRAINT fk_inv_tx_actor     FOREIGN KEY (actor_user_id) REFERENCES users(id)     ON DELETE RESTRICT,
  CONSTRAINT ck_inv_tx_type CHECK (type IN ('RECEIPT','RESERVE','RELEASE','DISPATCH','DELIVERY','ADJUSTMENT')),
  CONSTRAINT ck_inv_tx_quantity CHECK (quantity <> 0 AND (type <> 'RECEIPT' OR quantity > 0)),
  CONSTRAINT ck_inv_tx_after CHECK (on_hand_after >= 0 AND reserved_after >= 0)
);
CREATE INDEX idx_inv_tx_inventory ON inventory_transactions (inventory_id, created_at DESC);
CREATE INDEX idx_inv_tx_warehouse ON inventory_transactions (warehouse_id);
CREATE INDEX idx_inv_tx_resource  ON inventory_transactions (resource_id);
CREATE INDEX idx_inv_tx_reference ON inventory_transactions (reference_type, reference_id);

-- The ledger is history: rows can be added, never changed or removed (corrections are new ADJUSTMENT rows).
CREATE FUNCTION inventory_transactions_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'inventory_transactions is append-only' USING ERRCODE = 'check_violation';
END $$;
CREATE TRIGGER trg_inventory_transactions_immutable
  BEFORE UPDATE OR DELETE ON inventory_transactions
  FOR EACH ROW EXECUTE FUNCTION inventory_transactions_immutable();

CREATE TABLE shelters (
  id          BIGSERIAL PRIMARY KEY,
  disaster_id BIGINT,
  name        VARCHAR(200) NOT NULL,
  address     TEXT,
  district    VARCHAR(100),
  state       VARCHAR(100),
  capacity    INTEGER      NOT NULL,
  occupancy   INTEGER      NOT NULL DEFAULT 0,
  facilities  TEXT,
  accessible  BOOLEAN      NOT NULL DEFAULT FALSE,
  status      VARCHAR(10)  NOT NULL DEFAULT 'OPEN',
  location    geography(Point, 4326) NOT NULL,
  is_demo     BOOLEAN      NOT NULL DEFAULT FALSE,
  created_by  BIGINT,
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT fk_shelters_disaster FOREIGN KEY (disaster_id) REFERENCES disasters(id) ON DELETE RESTRICT,
  CONSTRAINT fk_shelters_creator  FOREIGN KEY (created_by)  REFERENCES users(id)     ON DELETE RESTRICT,
  CONSTRAINT ck_shelters_capacity  CHECK (capacity > 0),
  -- Occupancy may exceed capacity: that is exactly the "overcrowded" situation the system must detect and show.
  CONSTRAINT ck_shelters_occupancy CHECK (occupancy >= 0),
  CONSTRAINT ck_shelters_status    CHECK (status IN ('OPEN','CLOSED')),
  CONSTRAINT ck_shelters_name_not_blank CHECK (length(btrim(name)) > 0)
);
CREATE INDEX idx_shelters_status ON shelters (status);
CREATE INDEX idx_shelters_disaster ON shelters (disaster_id);
CREATE INDEX idx_shelters_location ON shelters USING GIST (location);
