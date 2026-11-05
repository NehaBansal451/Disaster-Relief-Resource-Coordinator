-- V3: disasters and affected areas (PROPOSED D3, pending Divay approval).

CREATE TABLE disasters (
  id            BIGSERIAL PRIMARY KEY,
  name          VARCHAR(200) NOT NULL,
  disaster_type VARCHAR(30)  NOT NULL,
  severity      VARCHAR(10)  NOT NULL,
  status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
  started_at    TIMESTAMPTZ  NOT NULL,
  ended_at      TIMESTAMPTZ,
  description   TEXT,
  epicentre     geography(Point, 4326),
  created_by    BIGINT,
  is_demo       BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT fk_disasters_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  CONSTRAINT ck_disasters_type CHECK (disaster_type IN
    ('FLOOD','EARTHQUAKE','CYCLONE','LANDSLIDE','FIRE','DROUGHT','HEATWAVE','OTHER')),
  CONSTRAINT ck_disasters_severity CHECK (severity IN ('CRITICAL','HIGH','MEDIUM','LOW')),
  CONSTRAINT ck_disasters_status CHECK (status IN ('ACTIVE','MONITORING','CLOSED')),
  CONSTRAINT ck_disasters_dates CHECK (ended_at IS NULL OR ended_at >= started_at),
  CONSTRAINT ck_disasters_closed_has_end CHECK (status <> 'CLOSED' OR ended_at IS NOT NULL),
  CONSTRAINT ck_disasters_name_not_blank CHECK (length(btrim(name)) > 0)
);
CREATE INDEX idx_disasters_status     ON disasters (status);
CREATE INDEX idx_disasters_severity   ON disasters (severity);
CREATE INDEX idx_disasters_started_at ON disasters (started_at DESC);
CREATE INDEX idx_disasters_created_by ON disasters (created_by);
CREATE INDEX gix_disasters_epicentre  ON disasters USING GIST (epicentre);

CREATE TABLE affected_areas (
  id                 BIGSERIAL PRIMARY KEY,
  disaster_id        BIGINT       NOT NULL,
  name               VARCHAR(200) NOT NULL,
  area_type          VARCHAR(20)  NOT NULL DEFAULT 'VILLAGE',
  district           VARCHAR(100),
  state              VARCHAR(100),
  population_estimate INTEGER     NOT NULL,
  impact_level       VARCHAR(10)  NOT NULL,
  impact_description TEXT,
  location           geography(Point, 4326) NOT NULL,          -- centre point, used for distance and map marker
  boundary           geography(MultiPolygon, 4326),            -- optional exact shape
  created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
  -- RESTRICT: deleting a disaster must never silently delete its areas.
  CONSTRAINT fk_affected_areas_disaster FOREIGN KEY (disaster_id) REFERENCES disasters(id) ON DELETE RESTRICT,
  CONSTRAINT uq_affected_areas_disaster_name UNIQUE NULLS NOT DISTINCT (disaster_id, name, district),
  CONSTRAINT ck_affected_areas_type CHECK (area_type IN ('VILLAGE','TOWN','WARD','DISTRICT')),
  CONSTRAINT ck_affected_areas_population CHECK (population_estimate >= 0),
  CONSTRAINT ck_affected_areas_impact CHECK (impact_level IN ('CRITICAL','HIGH','MEDIUM','LOW')),
  CONSTRAINT ck_affected_areas_name_not_blank CHECK (length(btrim(name)) > 0)
);
CREATE INDEX idx_affected_areas_disaster ON affected_areas (disaster_id);
CREATE INDEX idx_affected_areas_impact   ON affected_areas (impact_level);
CREATE INDEX gix_affected_areas_location ON affected_areas USING GIST (location);
CREATE INDEX gix_affected_areas_boundary ON affected_areas USING GIST (boundary);
