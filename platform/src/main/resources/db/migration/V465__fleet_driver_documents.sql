-- A driver's file: photo, licence and PrDP copies, medical certificate, ID copy, training certificates, contract.
-- Mirrors the vehicle vault: singleton types keep one CURRENT row (older versions stay as history), deletes are soft.
CREATE TABLE fleet_driver_documents (
    id               UUID PRIMARY KEY,
    tenant_id        UUID         NOT NULL,
    driver_id        UUID         NOT NULL REFERENCES fleet_drivers (id),
    doc_type         VARCHAR(40)  NOT NULL,
    title            VARCHAR(200),
    document_number  VARCHAR(100),
    issue_date       DATE,
    expiry_date      DATE,
    issuer           VARCHAR(255),
    notes            TEXT,
    document_version INTEGER      NOT NULL DEFAULT 1,
    status           VARCHAR(20)  NOT NULL,
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

CREATE INDEX idx_fleet_driver_documents_driver
    ON fleet_driver_documents (tenant_id, driver_id)
    WHERE deleted_at IS NULL;

-- One current document per singleton type per driver. KEEP IN SYNC with DriverDocumentType.isSingleton().
CREATE UNIQUE INDEX uq_fleet_driver_documents_current_singleton
    ON fleet_driver_documents (driver_id, doc_type)
    WHERE status = 'CURRENT'
      AND deleted_at IS NULL
      AND doc_type IN ('PHOTO', 'DRIVING_LICENCE', 'PRDP', 'MEDICAL_CERTIFICATE', 'ID_COPY', 'CONTRACT');
