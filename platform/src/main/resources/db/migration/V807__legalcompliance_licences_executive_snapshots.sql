-- ============================================================================
-- Legal / Compliance — licences, permits and certificates (engine 20) and executive posture snapshots.
-- Depends on V290. Confirm the next free Flyway version before applying.
--
--  * "Expired" is not stored: it is derived from expiry_date and today, so a licence cannot sit as ACTIVE in the data after it lapsed.
--  * A renewal keeps the proof files that were attached at submission (snapshot) and appends to renewal_history, so earlier expiry dates survive.
--  * Posture snapshots store raw counts (never a composite score) so any past percentage can be recomputed from what was recorded on the day.
-- ============================================================================

CREATE TABLE legalcompliance_licences (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    reference             VARCHAR(20) NOT NULL,
    licence_type          VARCHAR(15) NOT NULL,
    title                 VARCHAR(255) NOT NULL,
    issuing_authority     VARCHAR(255) NOT NULL,
    licence_number        VARCHAR(100),
    scope                 TEXT,
    issue_date            DATE,
    expiry_date           DATE,
    status                VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    owner_name            VARCHAR(255) NOT NULL,
    renewal_lead_days     INTEGER NOT NULL DEFAULT 90,
    obligation_id         UUID REFERENCES legalcompliance_regulatory_obligations (id),
    renewal_started_on    DATE,
    renewal_submitted_on  DATE,
    renewal_reference     VARCHAR(100),
    renewal_proof         JSONB,
    renewal_history       JSONB NOT NULL DEFAULT '[]'::jsonb,
    status_reason         TEXT,
    notes                 TEXT,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL,
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lc_licence_ref UNIQUE (tenant_id, reference),
    CONSTRAINT ck_lc_licence_type CHECK (licence_type IN ('LICENCE','PERMIT','CERTIFICATE','REGISTRATION','ACCREDITATION')),
    CONSTRAINT ck_lc_licence_status CHECK (status IN ('ACTIVE','RENEWAL_IN_PROGRESS','SUSPENDED','REVOKED','SURRENDERED')),
    CONSTRAINT ck_lc_licence_dates CHECK (issue_date IS NULL OR expiry_date IS NULL OR expiry_date >= issue_date),
    CONSTRAINT ck_lc_licence_lead CHECK (renewal_lead_days BETWEEN 1 AND 365)
);
CREATE INDEX idx_lc_licence_tenant ON legalcompliance_licences (tenant_id, status, expiry_date);
CREATE INDEX idx_lc_licence_expiry ON legalcompliance_licences (expiry_date) WHERE status IN ('ACTIVE','RENEWAL_IN_PROGRESS') AND expiry_date IS NOT NULL;

CREATE TABLE legalcompliance_licence_conditions (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    licence_id      UUID NOT NULL REFERENCES legalcompliance_licences (id) ON DELETE CASCADE,
    description     TEXT NOT NULL,
    due_date        DATE,
    frequency_months INTEGER,
    status          VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    last_met_on     DATE,
    note            TEXT,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_lc_cond_status CHECK (status IN ('PENDING','MET','BREACHED')),
    CONSTRAINT ck_lc_cond_freq CHECK (frequency_months IS NULL OR (frequency_months BETWEEN 1 AND 120 AND due_date IS NOT NULL))
);
CREATE INDEX idx_lc_cond_licence ON legalcompliance_licence_conditions (licence_id);
CREATE INDEX idx_lc_cond_due ON legalcompliance_licence_conditions (due_date) WHERE status = 'PENDING' AND due_date IS NOT NULL;

CREATE TABLE legalcompliance_posture_snapshots (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    snapshot_date   DATE NOT NULL,
    source          VARCHAR(10) NOT NULL,
    taken_by_name   VARCHAR(255),
    metrics         JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lc_snapshot_day UNIQUE (tenant_id, snapshot_date),
    CONSTRAINT ck_lc_snapshot_source CHECK (source IN ('MANUAL','MONTHLY'))
);
CREATE INDEX idx_lc_snapshot_tenant ON legalcompliance_posture_snapshots (tenant_id, snapshot_date DESC);

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'LEGALCOMPLIANCE_LICENCE_MANAGE', 'Manage licences, permits and certificates, their conditions and renewals')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name = 'LEGALCOMPLIANCE_LICENCE_MANAGE'
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
