-- Rename to the next free Flyway version, e.g. V124__fleet_documents_insurance.sql

CREATE TABLE fleet_vehicle_documents (
    id               UUID PRIMARY KEY,
    tenant_id        UUID         NOT NULL,
    vehicle_id       UUID         NOT NULL REFERENCES fleet_vehicles (id),
    doc_type         VARCHAR(40)  NOT NULL,
    document_number  VARCHAR(100),
    issue_date       DATE,
    expiry_date      DATE,
    issuer           VARCHAR(255),
    notes            TEXT,
    document_version INTEGER      NOT NULL DEFAULT 1,
    status           VARCHAR(20)  NOT NULL,           -- CURRENT | SUPERSEDED
    file_name        VARCHAR(255) NOT NULL,
    content_type     VARCHAR(100) NOT NULL,
    size_bytes       BIGINT       NOT NULL,
    storage_key      VARCHAR(500) NOT NULL,
    uploaded_by      UUID,
    uploaded_at      TIMESTAMPTZ  NOT NULL,
    superseded_at    TIMESTAMPTZ,
    deleted_at       TIMESTAMPTZ,
    deleted_by       UUID
);

CREATE INDEX idx_fleet_vehicle_documents_vehicle
    ON fleet_vehicle_documents (tenant_id, vehicle_id)
    WHERE deleted_at IS NULL;

-- At most ONE current document per "singleton" type per vehicle (a vehicle has one
-- current licence disc, one current roadworthy, ...). This is the DB-level backstop for
-- two simultaneous uploads. KEEP THIS LIST IN SYNC with VehicleDocumentType.isSingleton().
CREATE UNIQUE INDEX uq_fleet_vehicle_documents_current_singleton
    ON fleet_vehicle_documents (vehicle_id, doc_type)
    WHERE status = 'CURRENT'
      AND deleted_at IS NULL
      AND doc_type IN ('LICENCE_DISC', 'ROADWORTHY', 'INSURANCE', 'REGISTRATION_CERTIFICATE',
                       'PURCHASE_AGREEMENT', 'FINANCE_AGREEMENT', 'TRACKING_CERTIFICATE');

CREATE TABLE fleet_vehicle_insurance (
    id                       UUID PRIMARY KEY,
    tenant_id                UUID        NOT NULL,
    vehicle_id               UUID        NOT NULL UNIQUE REFERENCES fleet_vehicles (id),
    insured                  BOOLEAN     NOT NULL DEFAULT FALSE,
    insurer                  VARCHAR(255),
    policy_number            VARCHAR(100),
    policy_type              VARCHAR(30),
    policy_start             DATE,
    policy_expiry            DATE,
    premium                  NUMERIC(15, 2),
    excess                   NUMERIC(15, 2),
    broker                   VARCHAR(255),
    broker_contact           VARCHAR(255),
    cover_amount             NUMERIC(15, 2),
    vehicle_value            NUMERIC(15, 2),
    tracking_required        BOOLEAN     NOT NULL DEFAULT FALSE,
    tracking_provider        VARCHAR(255),
    tracking_device_number   VARCHAR(100),
    windscreen_cover         BOOLEAN     NOT NULL DEFAULT FALSE,
    third_party_cover_amount NUMERIC(15, 2),
    special_conditions       TEXT,
    created_at               TIMESTAMPTZ NOT NULL,
    updated_at               TIMESTAMPTZ NOT NULL,
    updated_by               UUID,
    version                  BIGINT      NOT NULL DEFAULT 0
);