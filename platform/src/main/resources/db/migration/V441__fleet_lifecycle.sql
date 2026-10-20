-- Rename to the next free Flyway version, e.g. V127__fleet_lifecycle.sql

-- Lifecycle status lives BESIDE the existing operational status (AVAILABLE / ON_TRIP / ...).
ALTER TABLE fleet_vehicles ADD COLUMN lifecycle_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
-- Vehicles already retired operationally are retired in the lifecycle too.
UPDATE fleet_vehicles SET lifecycle_status = 'RETIRED' WHERE status = 'RETIRED';
CREATE INDEX idx_fleet_vehicles_lifecycle ON fleet_vehicles (tenant_id, lifecycle_status) WHERE deleted_at IS NULL;

-- Every lifecycle change, as an audit trail. Type-specific detail hangs off event_id.
CREATE TABLE fleet_vehicle_lifecycle_events (
    id          UUID PRIMARY KEY,
    tenant_id   UUID         NOT NULL,
    vehicle_id  UUID         NOT NULL REFERENCES fleet_vehicles (id),
    event_type  VARCHAR(30)  NOT NULL,   -- STATUS_CHANGE | THEFT_REPORTED | RECOVERED | WRITE_OFF | DISPOSAL
    from_status VARCHAR(20)  NOT NULL,
    to_status   VARCHAR(20)  NOT NULL,
    occurred_at TIMESTAMPTZ  NOT NULL,   -- when it happened (e.g. when the vehicle was stolen)
    recorded_at TIMESTAMPTZ  NOT NULL,   -- when it was entered
    reason      VARCHAR(500),
    note        TEXT,
    recorded_by UUID
);
CREATE INDEX idx_fleet_lifecycle_events_vehicle
    ON fleet_vehicle_lifecycle_events (tenant_id, vehicle_id, occurred_at DESC);

-- Accidents. The table was missing from the fleet migrations although the vehicle lifecycle, driver scoring and workshop tables below
-- all point at it, so it is created here, ahead of its first use.
CREATE TABLE fleet_accidents (
    id                           UUID PRIMARY KEY,
    tenant_id                    UUID NOT NULL,
    reference_number             VARCHAR(30) NOT NULL,
    vehicle_id                   UUID NOT NULL REFERENCES fleet_vehicles (id),
    driver_id                    UUID,
    driver_name                  VARCHAR(255),
    trip_id                      UUID,
    occurred_at                  TIMESTAMPTZ NOT NULL,
    location                     VARCHAR(500),
    weather                      VARCHAR(20),
    road_condition               VARCHAR(20),
    other_vehicle_registration   VARCHAR(30),
    other_driver_name            VARCHAR(255),
    other_driver_contact         VARCHAR(255),
    other_insurer                VARCHAR(255),
    police_case_number           VARCHAR(100),
    saps_station                 VARCHAR(255),
    police_reference             VARCHAR(100),
    description                  TEXT NOT NULL,
    witnesses                    TEXT,
    injuries_count               INTEGER NOT NULL DEFAULT 0,
    fatalities_count             INTEGER NOT NULL DEFAULT 0,
    estimated_damage             NUMERIC(15,2),
    third_party_damage           NUMERIC(15,2),
    vehicle_drivable             BOOLEAN NOT NULL DEFAULT TRUE,
    status                       VARCHAR(20) NOT NULL DEFAULT 'REPORTED',
    resolution_note              TEXT,
    closed_at                    TIMESTAMPTZ,
    claim_status                 VARCHAR(20) NOT NULL DEFAULT 'NOT_CLAIMED',
    claim_insurer                VARCHAR(255),
    claim_number                 VARCHAR(100),
    claim_excess                 NUMERIC(15,2),
    assessor_name                VARCHAR(255),
    assessment_date              DATE,
    claim_submitted_at           DATE,
    claim_settlement_amount      NUMERIC(15,2),
    claim_settled_at             DATE,
    repairer                     VARCHAR(255),
    repair_cost                  NUMERIC(15,2),
    returned_to_service_at       DATE,
    reported_by                  UUID,
    created_at                   TIMESTAMPTZ NOT NULL,
    updated_at                   TIMESTAMPTZ NOT NULL,
    version                      BIGINT
);
CREATE UNIQUE INDEX uq_fleet_accidents_reference ON fleet_accidents (tenant_id, reference_number);
CREATE INDEX idx_fleet_accidents_vehicle ON fleet_accidents (tenant_id, vehicle_id, occurred_at DESC);

CREATE TABLE fleet_vehicle_disposals (
    id                         UUID PRIMARY KEY,
    tenant_id                  UUID         NOT NULL,
    vehicle_id                 UUID         NOT NULL REFERENCES fleet_vehicles (id),
    event_id                   UUID         NOT NULL UNIQUE REFERENCES fleet_vehicle_lifecycle_events (id),
    disposal_method            VARCHAR(20),
    counterparty_name          VARCHAR(255),   -- buyer / transferee / scrapper
    counterparty_contact       VARCHAR(255),
    disposal_date              DATE         NOT NULL,
    sale_price                 NUMERIC(15, 2),
    odometer_at_disposal       INTEGER,
    book_value_at_disposal     NUMERIC(15, 2), -- snapshot of the vehicle's book value when disposed
    finance_cleared            BOOLEAN      NOT NULL DEFAULT FALSE,
    finance_settlement_amount  NUMERIC(15, 2),
    registration_transferred   BOOLEAN      NOT NULL DEFAULT FALSE,
    registration_transfer_date DATE,
    accounting_reference       VARCHAR(100),
    notes                      TEXT
);

CREATE TABLE fleet_vehicle_theft_reports (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID         NOT NULL,
    vehicle_id         UUID         NOT NULL REFERENCES fleet_vehicles (id),
    event_id           UUID         NOT NULL UNIQUE REFERENCES fleet_vehicle_lifecycle_events (id),
    kind               VARCHAR(10)  NOT NULL,       -- STOLEN | MISSING
    occurred_at        TIMESTAMPTZ  NOT NULL,       -- when stolen / last seen
    location           VARCHAR(500),
    police_case_number VARCHAR(100),
    saps_station       VARCHAR(255),
    police_reference   VARCHAR(100),
    insurer            VARCHAR(255),
    claim_number       VARCHAR(100),
    tracking_info      TEXT,
    notes              TEXT,
    recovery_status    VARCHAR(20)  NOT NULL DEFAULT 'NOT_RECOVERED', -- NOT_RECOVERED | RECOVERED | RECOVERED_DAMAGED | SUPERSEDED
    recovered_at       TIMESTAMPTZ,
    recovery_location  VARCHAR(500),
    recovery_notes     TEXT
);
CREATE INDEX idx_fleet_theft_reports_vehicle ON fleet_vehicle_theft_reports (tenant_id, vehicle_id, occurred_at DESC);

CREATE TABLE fleet_vehicle_write_offs (
    id                           UUID PRIMARY KEY,
    tenant_id                    UUID         NOT NULL,
    vehicle_id                   UUID         NOT NULL REFERENCES fleet_vehicles (id),
    event_id                     UUID         NOT NULL UNIQUE REFERENCES fleet_vehicle_lifecycle_events (id),
    accident_id                  UUID         REFERENCES fleet_accidents (id),
    assessment_date              DATE,
    assessor_name                VARCHAR(255),
    decision_date                DATE         NOT NULL,
    decision_note                TEXT,
    pre_accident_value           NUMERIC(15, 2),
    salvage_value                NUMERIC(15, 2),
    salvage_buyer                VARCHAR(255),
    salvage_sold_at              DATE,
    insurance_settlement_amount  NUMERIC(15, 2),
    insurance_settled_at         DATE,
    created_at                   TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_fleet_write_offs_vehicle ON fleet_vehicle_write_offs (tenant_id, vehicle_id, created_at DESC);

CREATE TABLE fleet_vehicle_lifecycle_evidence (
    id            UUID PRIMARY KEY,
    tenant_id     UUID         NOT NULL,
    vehicle_id    UUID         NOT NULL REFERENCES fleet_vehicles (id),
    event_id      UUID         NOT NULL REFERENCES fleet_vehicle_lifecycle_events (id),
    evidence_type VARCHAR(40)  NOT NULL,
    description   VARCHAR(500),
    file_name     VARCHAR(255) NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    storage_key   VARCHAR(500) NOT NULL,
    uploaded_by   UUID,
    uploaded_at   TIMESTAMPTZ  NOT NULL,
    deleted_at    TIMESTAMPTZ,
    deleted_by    UUID
);
CREATE INDEX idx_fleet_lifecycle_evidence_vehicle
    ON fleet_vehicle_lifecycle_evidence (tenant_id, vehicle_id) WHERE deleted_at IS NULL;