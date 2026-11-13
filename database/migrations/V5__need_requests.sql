-- V5: emergency need requests and their history (Day 4, PROPOSED, pending Divay approval).
-- Priority columns exist now (nullable) because the API returns them; the scoring itself is Day 6.

-- A request must reference an area that belongs to the SAME disaster. A composite foreign key enforces that in the database.
ALTER TABLE affected_areas ADD CONSTRAINT uq_affected_areas_id_disaster UNIQUE (id, disaster_id);

CREATE TABLE need_requests (
  id                  BIGSERIAL PRIMARY KEY,
  disaster_id         BIGINT       NOT NULL,
  affected_area_id    BIGINT       NOT NULL,
  resource_id         BIGINT       NOT NULL,
  requested_by        BIGINT       NOT NULL,
  quantity            INTEGER      NOT NULL,
  affected_population INTEGER      NOT NULL DEFAULT 0,
  severity            VARCHAR(10)  NOT NULL,
  description         TEXT         NOT NULL,
  status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
  status_reason       TEXT,
  priority_score      NUMERIC(5,2),
  priority_category   VARCHAR(10),
  approved_by         BIGINT,
  approved_at         TIMESTAMPTZ,
  is_demo             BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT fk_need_requests_disaster  FOREIGN KEY (disaster_id) REFERENCES disasters(id) ON DELETE RESTRICT,
  CONSTRAINT fk_need_requests_area      FOREIGN KEY (affected_area_id, disaster_id)
                                        REFERENCES affected_areas(id, disaster_id) ON DELETE RESTRICT,
  CONSTRAINT fk_need_requests_resource  FOREIGN KEY (resource_id) REFERENCES resources(id) ON DELETE RESTRICT,
  CONSTRAINT fk_need_requests_requester FOREIGN KEY (requested_by) REFERENCES users(id) ON DELETE RESTRICT,
  CONSTRAINT fk_need_requests_approver  FOREIGN KEY (approved_by) REFERENCES users(id) ON DELETE RESTRICT,
  CONSTRAINT ck_need_requests_quantity   CHECK (quantity > 0),
  CONSTRAINT ck_need_requests_population CHECK (affected_population >= 0),
  CONSTRAINT ck_need_requests_severity   CHECK (severity IN ('CRITICAL','HIGH','MEDIUM','LOW')),
  CONSTRAINT ck_need_requests_status     CHECK (status IN
    ('PENDING','VERIFIED','APPROVED','ALLOCATED','DISPATCHED','IN_TRANSIT','DELIVERED','REJECTED','CANCELLED')),
  CONSTRAINT ck_need_requests_priority_score    CHECK (priority_score IS NULL OR priority_score BETWEEN 0 AND 100),
  CONSTRAINT ck_need_requests_priority_category CHECK (priority_category IS NULL OR priority_category IN ('CRITICAL','HIGH','MEDIUM','LOW')),
  CONSTRAINT ck_need_requests_description CHECK (length(btrim(description)) BETWEEN 1 AND 2000),
  -- A rejection or cancellation must always carry a reason (API contract section 1).
  CONSTRAINT ck_need_requests_reason CHECK (status NOT IN ('REJECTED','CANCELLED') OR length(btrim(coalesce(status_reason,''))) > 0),
  -- An approved request must say who approved it and when.
  CONSTRAINT ck_need_requests_approval CHECK (status <> 'APPROVED' OR (approved_by IS NOT NULL AND approved_at IS NOT NULL))
);
CREATE INDEX idx_need_requests_status     ON need_requests (status);
CREATE INDEX idx_need_requests_severity   ON need_requests (severity);
CREATE INDEX idx_need_requests_disaster   ON need_requests (disaster_id);
CREATE INDEX idx_need_requests_area       ON need_requests (affected_area_id);
CREATE INDEX idx_need_requests_resource   ON need_requests (resource_id);
CREATE INDEX idx_need_requests_requester  ON need_requests (requested_by);
CREATE INDEX idx_need_requests_created_at ON need_requests (created_at DESC);

CREATE TABLE request_history (
  id            BIGSERIAL PRIMARY KEY,
  request_id    BIGINT      NOT NULL,
  action        VARCHAR(30) NOT NULL,
  from_status   VARCHAR(20),
  to_status     VARCHAR(20) NOT NULL,
  actor_user_id BIGINT      NOT NULL,
  reason        TEXT,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT fk_request_history_request FOREIGN KEY (request_id) REFERENCES need_requests(id) ON DELETE RESTRICT,
  CONSTRAINT fk_request_history_actor   FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE RESTRICT,
  CONSTRAINT ck_request_history_action CHECK (action IN
    ('CREATED','VERIFIED','REJECTED','CLARIFICATION_REQUESTED','APPROVED','CANCELLED'))
);
CREATE INDEX idx_request_history_request ON request_history (request_id, created_at);
CREATE INDEX idx_request_history_actor   ON request_history (actor_user_id);
