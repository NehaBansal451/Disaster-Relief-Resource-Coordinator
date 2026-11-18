-- V9: explainable priority fields on requests; allocations, allocation items, stock reservations (Day 6, PROPOSED).

ALTER TABLE need_requests
  ADD COLUMN priority_reasons         TEXT,
  ADD COLUMN priority_calculated_at   TIMESTAMPTZ,
  ADD COLUMN priority_overridden      BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN priority_override_reason TEXT,
  ADD CONSTRAINT ck_need_requests_priority_pair CHECK ((priority_score IS NULL) = (priority_category IS NULL)),
  ADD CONSTRAINT ck_need_requests_override_reason
      CHECK (NOT priority_overridden OR length(btrim(coalesce(priority_override_reason, ''))) > 0);

ALTER TABLE request_history DROP CONSTRAINT ck_request_history_action;
ALTER TABLE request_history ADD CONSTRAINT ck_request_history_action CHECK (action IN
  ('CREATED','VERIFIED','REJECTED','CLARIFICATION_REQUESTED','APPROVED','CANCELLED',
   'PRIORITY_CALCULATED','PRIORITY_OVERRIDDEN','ALLOCATED','DISPATCHED','IN_TRANSIT','DELIVERED'));

CREATE TABLE allocations (
  id            BIGSERIAL PRIMARY KEY,
  request_id    BIGINT      NOT NULL,
  status        VARCHAR(12) NOT NULL DEFAULT 'RECOMMENDED',
  requested_qty INTEGER     NOT NULL,
  allocated_qty INTEGER     NOT NULL,
  version       INTEGER     NOT NULL DEFAULT 1,
  explanation   TEXT,
  note          TEXT,
  created_by    BIGINT      NOT NULL,
  approved_by   BIGINT,
  approved_at   TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT fk_allocations_request  FOREIGN KEY (request_id)  REFERENCES need_requests(id) ON DELETE RESTRICT,
  CONSTRAINT fk_allocations_creator  FOREIGN KEY (created_by)  REFERENCES users(id) ON DELETE RESTRICT,
  CONSTRAINT fk_allocations_approver FOREIGN KEY (approved_by) REFERENCES users(id) ON DELETE RESTRICT,
  CONSTRAINT ck_allocations_status CHECK (status IN ('RECOMMENDED','APPROVED','STALE','REJECTED')),
  CONSTRAINT ck_allocations_requested CHECK (requested_qty > 0),
  CONSTRAINT ck_allocations_allocated CHECK (allocated_qty >= 0 AND allocated_qty <= requested_qty),
  CONSTRAINT ck_allocations_approval CHECK (status <> 'APPROVED' OR (approved_by IS NOT NULL AND approved_at IS NOT NULL AND allocated_qty > 0))
);
-- A request can have at most one live allocation (recommended or approved): no double allocation of one request.
CREATE UNIQUE INDEX uq_allocations_one_live ON allocations (request_id) WHERE status IN ('RECOMMENDED','APPROVED');
CREATE INDEX idx_allocations_request ON allocations (request_id);
CREATE INDEX idx_allocations_status  ON allocations (status);

CREATE TABLE allocation_items (
  id            BIGSERIAL PRIMARY KEY,
  allocation_id BIGINT       NOT NULL,
  warehouse_id  BIGINT       NOT NULL,
  quantity      INTEGER      NOT NULL,
  distance_km   NUMERIC(8,1),
  reason        TEXT         NOT NULL,
  CONSTRAINT fk_allocation_items_allocation FOREIGN KEY (allocation_id) REFERENCES allocations(id) ON DELETE RESTRICT,
  CONSTRAINT fk_allocation_items_warehouse  FOREIGN KEY (warehouse_id)  REFERENCES warehouses(id)  ON DELETE RESTRICT,
  CONSTRAINT uq_allocation_items UNIQUE (allocation_id, warehouse_id),
  CONSTRAINT ck_allocation_items_quantity CHECK (quantity > 0)
);
CREATE INDEX idx_allocation_items_warehouse ON allocation_items (warehouse_id);

CREATE TABLE allocation_not_selected (
  id            BIGSERIAL PRIMARY KEY,
  allocation_id BIGINT NOT NULL,
  warehouse_id  BIGINT NOT NULL,
  reason        TEXT   NOT NULL,
  CONSTRAINT fk_alloc_not_selected_allocation FOREIGN KEY (allocation_id) REFERENCES allocations(id) ON DELETE RESTRICT,
  CONSTRAINT fk_alloc_not_selected_warehouse  FOREIGN KEY (warehouse_id)  REFERENCES warehouses(id)  ON DELETE RESTRICT,
  CONSTRAINT uq_alloc_not_selected UNIQUE (allocation_id, warehouse_id)
);

-- Which inventory lines an approved allocation reserved (needed to deduct exactly those lines at dispatch).
CREATE TABLE allocation_reservations (
  id            BIGSERIAL PRIMARY KEY,
  allocation_id BIGINT  NOT NULL,
  inventory_id  BIGINT  NOT NULL,
  quantity      INTEGER NOT NULL,
  dispatched    BOOLEAN NOT NULL DEFAULT FALSE,
  CONSTRAINT fk_alloc_res_allocation FOREIGN KEY (allocation_id) REFERENCES allocations(id) ON DELETE RESTRICT,
  CONSTRAINT fk_alloc_res_inventory  FOREIGN KEY (inventory_id)  REFERENCES inventory(id)  ON DELETE RESTRICT,
  CONSTRAINT uq_alloc_res UNIQUE (allocation_id, inventory_id),
  CONSTRAINT ck_alloc_res_quantity CHECK (quantity > 0)
);
CREATE INDEX idx_alloc_res_inventory ON allocation_reservations (inventory_id);
