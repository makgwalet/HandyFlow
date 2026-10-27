-- Trade licences and tickets held by technicians (wireman's, gas, plumbing, working at heights...).
CREATE TABLE IF NOT EXISTS facility_technician_licences (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    technician_id   UUID         NOT NULL,
    licence_type    VARCHAR(40)  NOT NULL,
    reference       VARCHAR(120),
    issued_by       VARCHAR(200),
    issue_date      DATE,
    expiry_date     DATE,
    status          VARCHAR(20)  NOT NULL DEFAULT 'VALID',
    revoked_reason  VARCHAR(500),
    revoked_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_facility_tech_lic_tech ON facility_technician_licences (tenant_id, technician_id);
CREATE INDEX IF NOT EXISTS idx_facility_tech_lic_expiry ON facility_technician_licences (expiry_date) WHERE status = 'VALID';
