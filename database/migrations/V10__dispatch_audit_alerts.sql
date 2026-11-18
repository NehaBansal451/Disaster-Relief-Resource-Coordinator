-- V10: dispatches, delivery timeline, audit log, alerts (Day 7, PROPOSED).

CREATE TABLE dispatches (
  id            BIGSERIAL PRIMARY KEY,
  allocation_id BIGINT       NOT NULL,
  request_id    BIGINT       NOT NULL,
  warehouse_id  BIGINT       NOT NULL,
  status        VARCHAR(12)  NOT NULL DEFAULT 'DISPATCHED',
  quantity      INTEGER      NOT NULL,
  vehicle       VARCHAR(100),
  driver_name   VARCHAR(150),
  note          TEXT,
  created_by    BIGINT       NOT NULL,
  dispatched_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
  delivered_at  TIMESTAMPTZ,
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT fk_dispatches_allocation FOREIGN KEY (allocation_id) REFERENCES allocations(id) ON DELETE RESTRICT,
  CONSTRAINT fk_dispatches_request    FOREIGN KEY (request_id)    REFERENCES need_requests(id) ON DELETE RESTRICT,
  CONSTRAINT fk_dispatches_warehouse  FOREIGN KEY (warehouse_id)  REFERENCES warehouses(id) ON DELETE RESTRICT,
  CONSTRAINT fk_dispatches_creator    FOREIGN KEY (created_by)    REFERENCES users(id) ON DELETE RESTRICT,
  CONSTRAINT uq_dispatches_allocation_warehouse UNIQUE (allocation_id, warehouse_id),
  CONSTRAINT ck_dispatches_status CHECK (status IN ('DISPATCHED','IN_TRANSIT','DELIVERED')),
  CONSTRAINT ck_dispatches_quantity CHECK (quantity > 0),
  CONSTRAINT ck_dispatches_delivered CHECK ((status = 'DELIVERED') = (delivered_at IS NOT NULL))
);
CREATE INDEX idx_dispatches_request   ON dispatches (request_id);
CREATE INDEX idx_dispatches_warehouse ON dispatches (warehouse_id);
CREATE INDEX idx_dispatches_status    ON dispatches (status);

CREATE TABLE delivery_updates (
  id            BIGSERIAL PRIMARY KEY,
  dispatch_id   BIGINT      NOT NULL,
  status        VARCHAR(12) NOT NULL,
  note          TEXT,
  actor_user_id BIGINT      NOT NULL,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT fk_delivery_updates_dispatch FOREIGN KEY (dispatch_id) REFERENCES dispatches(id) ON DELETE RESTRICT,
  CONSTRAINT fk_delivery_updates_actor    FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE RESTRICT,
  CONSTRAINT ck_delivery_updates_status CHECK (status IN ('DISPATCHED','IN_TRANSIT','DELIVERED'))
);
CREATE INDEX idx_delivery_updates_dispatch ON delivery_updates (dispatch_id, created_at);

CREATE TABLE audit_logs (
  id            BIGSERIAL PRIMARY KEY,
  actor_user_id BIGINT      NOT NULL,
  actor_role    VARCHAR(30) NOT NULL,
  action        VARCHAR(60) NOT NULL,
  entity_type   VARCHAR(40) NOT NULL,
  entity_id     BIGINT,
  details       TEXT,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT fk_audit_actor FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE RESTRICT,
  CONSTRAINT ck_audit_action_not_blank CHECK (length(btrim(action)) > 0)
);
CREATE INDEX idx_audit_entity  ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_actor   ON audit_logs (actor_user_id);
CREATE INDEX idx_audit_created ON audit_logs (created_at DESC);

CREATE FUNCTION audit_logs_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'audit_logs is append-only' USING ERRCODE = 'check_violation';
END $$;
CREATE TRIGGER trg_audit_logs_immutable BEFORE UPDATE OR DELETE ON audit_logs
  FOR EACH ROW EXECUTE FUNCTION audit_logs_immutable();

CREATE TABLE alerts (
  id          BIGSERIAL PRIMARY KEY,
  type        VARCHAR(30)  NOT NULL,
  severity    VARCHAR(10)  NOT NULL,
  message     VARCHAR(500) NOT NULL,
  entity_type VARCHAR(40),
  entity_id   BIGINT,
  status      VARCHAR(6)   NOT NULL DEFAULT 'NEW',
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  read_by     BIGINT,
  read_at     TIMESTAMPTZ,
  CONSTRAINT fk_alerts_reader FOREIGN KEY (read_by) REFERENCES users(id) ON DELETE RESTRICT,
  CONSTRAINT ck_alerts_type CHECK (type IN
    ('CRITICAL_REQUEST','LOW_STOCK','OVERCROWDED_SHELTER','REQUEST_APPROVED','DISPATCHED','DELIVERED')),
  CONSTRAINT ck_alerts_severity CHECK (severity IN ('CRITICAL','HIGH','MEDIUM','LOW')),
  CONSTRAINT ck_alerts_status CHECK (status IN ('NEW','READ')),
  CONSTRAINT ck_alerts_read CHECK ((status = 'READ') = (read_at IS NOT NULL))
);
CREATE INDEX idx_alerts_status  ON alerts (status);
CREATE INDEX idx_alerts_created ON alerts (created_at DESC);
