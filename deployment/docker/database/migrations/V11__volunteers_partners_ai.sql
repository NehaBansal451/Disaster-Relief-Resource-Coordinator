-- V11: volunteers, assignments, partner contributions, AI analysis records (Day 8, PROPOSED).

CREATE TABLE volunteers (
  id              BIGSERIAL PRIMARY KEY,
  user_id         BIGINT,
  organization_id BIGINT,
  full_name       VARCHAR(150) NOT NULL,
  phone           VARCHAR(30),
  skills          VARCHAR(500),
  availability    VARCHAR(10)  NOT NULL DEFAULT 'AVAILABLE',
  district        VARCHAR(100),
  state           VARCHAR(100),
  location        geography(Point, 4326),
  is_demo         BOOLEAN      NOT NULL DEFAULT FALSE,
  created_by      BIGINT       NOT NULL,
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT fk_volunteers_user    FOREIGN KEY (user_id)         REFERENCES users(id) ON DELETE RESTRICT,
  CONSTRAINT fk_volunteers_org     FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT,
  CONSTRAINT fk_volunteers_creator FOREIGN KEY (created_by)      REFERENCES users(id) ON DELETE RESTRICT,
  CONSTRAINT ck_volunteers_availability CHECK (availability IN ('AVAILABLE','BUSY','OFF_DUTY')),
  CONSTRAINT ck_volunteers_name CHECK (length(btrim(full_name)) > 0)
);
CREATE UNIQUE INDEX uq_volunteers_user ON volunteers (user_id) WHERE user_id IS NOT NULL;
CREATE INDEX idx_volunteers_availability ON volunteers (availability);
CREATE INDEX idx_volunteers_district ON volunteers (lower(district));

CREATE TABLE volunteer_assignments (
  id           BIGSERIAL PRIMARY KEY,
  volunteer_id BIGINT       NOT NULL,
  disaster_id  BIGINT       NOT NULL,
  request_id   BIGINT,
  task         VARCHAR(500) NOT NULL,
  status       VARCHAR(10)  NOT NULL DEFAULT 'ASSIGNED',
  assigned_by  BIGINT       NOT NULL,
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT fk_vol_assign_volunteer FOREIGN KEY (volunteer_id) REFERENCES volunteers(id) ON DELETE RESTRICT,
  CONSTRAINT fk_vol_assign_disaster  FOREIGN KEY (disaster_id)  REFERENCES disasters(id) ON DELETE RESTRICT,
  CONSTRAINT fk_vol_assign_request   FOREIGN KEY (request_id)   REFERENCES need_requests(id) ON DELETE RESTRICT,
  CONSTRAINT fk_vol_assign_by        FOREIGN KEY (assigned_by)  REFERENCES users(id) ON DELETE RESTRICT,
  CONSTRAINT ck_vol_assign_status CHECK (status IN ('ASSIGNED','ACCEPTED','DECLINED','COMPLETED')),
  CONSTRAINT ck_vol_assign_task CHECK (length(btrim(task)) > 0)
);
CREATE INDEX idx_vol_assign_volunteer ON volunteer_assignments (volunteer_id);
CREATE INDEX idx_vol_assign_disaster  ON volunteer_assignments (disaster_id);

CREATE TABLE partner_contributions (
  id              BIGSERIAL PRIMARY KEY,
  organization_id BIGINT       NOT NULL,
  resource_id     BIGINT       NOT NULL,
  quantity        INTEGER      NOT NULL,
  description     VARCHAR(1000),
  status          VARCHAR(10)  NOT NULL DEFAULT 'OFFERED',
  decided_by      BIGINT,
  created_by      BIGINT       NOT NULL,
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT fk_contrib_org      FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT,
  CONSTRAINT fk_contrib_resource FOREIGN KEY (resource_id)     REFERENCES resources(id) ON DELETE RESTRICT,
  CONSTRAINT fk_contrib_decider  FOREIGN KEY (decided_by)      REFERENCES users(id) ON DELETE RESTRICT,
  CONSTRAINT fk_contrib_creator  FOREIGN KEY (created_by)      REFERENCES users(id) ON DELETE RESTRICT,
  CONSTRAINT ck_contrib_quantity CHECK (quantity > 0),
  CONSTRAINT ck_contrib_status CHECK (status IN ('OFFERED','ACCEPTED','DECLINED','RECEIVED'))
);
CREATE INDEX idx_contrib_org ON partner_contributions (organization_id);
CREATE INDEX idx_contrib_status ON partner_contributions (status);

-- AI output is stored separately as an UNVERIFIED suggestion; it never changes a request by itself.
CREATE TABLE ai_analyses (
  id            BIGSERIAL PRIMARY KEY,
  report_text   TEXT         NOT NULL,
  model         VARCHAR(100),
  status        VARCHAR(10)  NOT NULL,
  result_json   TEXT,
  error_code    VARCHAR(40),
  review_status VARCHAR(10)  NOT NULL DEFAULT 'UNREVIEWED',
  reviewed_by   BIGINT,
  reviewed_at   TIMESTAMPTZ,
  created_by    BIGINT       NOT NULL,
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT fk_ai_reviewer FOREIGN KEY (reviewed_by) REFERENCES users(id) ON DELETE RESTRICT,
  CONSTRAINT fk_ai_creator  FOREIGN KEY (created_by)  REFERENCES users(id) ON DELETE RESTRICT,
  CONSTRAINT ck_ai_status CHECK (status IN ('SUCCEEDED','FAILED')),
  CONSTRAINT ck_ai_review CHECK (review_status IN ('UNREVIEWED','ACCEPTED','REJECTED')),
  CONSTRAINT ck_ai_result CHECK ((status = 'SUCCEEDED') = (result_json IS NOT NULL)),
  CONSTRAINT ck_ai_reviewed CHECK ((review_status = 'UNREVIEWED') = (reviewed_by IS NULL))
);
CREATE INDEX idx_ai_created ON ai_analyses (created_at DESC);
