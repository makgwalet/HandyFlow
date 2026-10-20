-- Accident reporting: per-tenant yearly reference counter (ACC-2026-00001) and the files kept with each case.
CREATE TABLE fleet_accident_counters (
    tenant_id   UUID    NOT NULL,
    year        INTEGER NOT NULL,
    last_number INTEGER NOT NULL,
    PRIMARY KEY (tenant_id, year)
);

CREATE TABLE fleet_accident_evidence (
    id            UUID PRIMARY KEY,
    tenant_id     UUID         NOT NULL,
    accident_id   UUID         NOT NULL REFERENCES fleet_accidents (id),
    evidence_type VARCHAR(30)  NOT NULL,
    description   VARCHAR(500),
    file_name     VARCHAR(255) NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    storage_key   VARCHAR(500) NOT NULL,
    uploaded_by   UUID,
    uploaded_at   TIMESTAMPTZ  NOT NULL,
    deleted_at    TIMESTAMPTZ,
    deleted_by    UUID,
    CONSTRAINT chk_fleet_accident_evidence_type CHECK (evidence_type IN
        ('PHOTO','POLICE_REPORT','THIRD_PARTY_DETAILS','ASSESSOR_REPORT','QUOTE','INVOICE','OTHER'))
);
CREATE INDEX idx_fleet_accident_evidence_accident ON fleet_accident_evidence (tenant_id, accident_id) WHERE deleted_at IS NULL;
