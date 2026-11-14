-- V8: shelter facilities, capacity and occupancy.
-- PostgreSQL 16 + PostGIS 3.4.
-- Warehouses already exist in V4; this migration adds shelters only.
-- A shelter is a reusable facility and is not tied to one disaster record.

CREATE TABLE shelters (
    id                   BIGSERIAL PRIMARY KEY,
    code                 VARCHAR(30)  NOT NULL,
    name                 VARCHAR(200) NOT NULL,
    organization_id      BIGINT,
    manager_user_id      BIGINT,
    address              TEXT,
    district             VARCHAR(100),
    state                VARCHAR(100),
    contact_phone        VARCHAR(30),
    capacity_people      INTEGER      NOT NULL,
    current_occupancy    INTEGER      NOT NULL DEFAULT 0,
    status               VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    has_food_support     BOOLEAN      NOT NULL DEFAULT FALSE,
    has_water_support    BOOLEAN      NOT NULL DEFAULT FALSE,
    has_medical_support  BOOLEAN      NOT NULL DEFAULT FALSE,
    is_accessible        BOOLEAN      NOT NULL DEFAULT FALSE,
    facility_notes       TEXT,
    location             geography(Point, 4326) NOT NULL,
    is_demo              BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_shelters_code UNIQUE (code),
    CONSTRAINT fk_shelters_organization FOREIGN KEY (organization_id)
        REFERENCES organizations(id) ON DELETE RESTRICT,
    CONSTRAINT fk_shelters_manager FOREIGN KEY (manager_user_id)
        REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT ck_shelters_code_not_blank CHECK (length(btrim(code)) > 0),
    CONSTRAINT ck_shelters_name_not_blank CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_shelters_capacity_positive CHECK (capacity_people > 0),
    CONSTRAINT ck_shelters_occupancy_nonnegative CHECK (current_occupancy >= 0),
    CONSTRAINT ck_shelters_status CHECK (status IN ('OPEN', 'CLOSED', 'INACTIVE')),
    CONSTRAINT ck_shelters_contact_phone CHECK (
        contact_phone IS NULL OR length(btrim(contact_phone)) > 0
    )
);

CREATE INDEX idx_shelters_status
    ON shelters(status);
CREATE INDEX idx_shelters_district_state
    ON shelters(district, state);
CREATE INDEX idx_shelters_organization
    ON shelters(organization_id);
CREATE INDEX idx_shelters_manager
    ON shelters(manager_user_id);
CREATE INDEX idx_shelters_occupancy
    ON shelters(status, current_occupancy, capacity_people);
CREATE INDEX gix_shelters_location
    ON shelters USING GIST(location);

-- Keep capacity calculations consistent for dashboard and alternative-shelter queries.
-- Closed/inactive shelters have no operationally available places.
-- Over-capacity occupancy is deliberately allowed and reported as overcrowding.
CREATE VIEW shelter_capacity_summary AS
SELECT
    s.id,
    s.code,
    s.name,
    s.status,
    s.district,
    s.state,
    s.location,
    s.capacity_people,
    s.current_occupancy,
    CASE WHEN s.status = 'OPEN'
         THEN GREATEST(s.capacity_people - s.current_occupancy, 0)
         ELSE 0
    END AS available_capacity,
    (s.status = 'OPEN' AND s.current_occupancy >= s.capacity_people) AS is_full,
    (s.current_occupancy > s.capacity_people) AS is_overcrowded
FROM shelters s;

COMMENT ON TABLE shelters IS
    'Reusable shelter facilities. Current occupancy is a reported people count and may exceed planned capacity to record overcrowding.';
COMMENT ON COLUMN shelters.capacity_people IS
    'Planned maximum capacity expressed as people, not beds or rooms.';
COMMENT ON COLUMN shelters.current_occupancy IS
    'Current reported people count. Values above capacity_people are allowed so overcrowding can be recorded and alerted.';
COMMENT ON COLUMN shelters.location IS
    'Shelter point location in WGS 84 (longitude/latitude, SRID 4326).';
COMMENT ON VIEW shelter_capacity_summary IS
    'Derived open shelter capacity. available_capacity is never negative; overcrowding is reported separately.';
