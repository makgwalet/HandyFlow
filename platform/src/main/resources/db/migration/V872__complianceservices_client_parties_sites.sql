-- Compliance Services: who owns and runs a client (directors, shareholders, beneficial owners)
-- and where it operates (sites).

CREATE TABLE IF NOT EXISTS compliance_client_parties (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    client_id        UUID NOT NULL REFERENCES compliance_service_clients(id) ON DELETE CASCADE,
    full_name        VARCHAR(255) NOT NULL,
    party_type       VARCHAR(10) NOT NULL DEFAULT 'PERSON',   -- PERSON, ENTITY
    id_type          VARCHAR(10),                              -- SA_ID, PASSPORT, REG_NO, OTHER
    id_number        VARCHAR(40),
    role             VARCHAR(20) NOT NULL,                     -- DIRECTOR, SHAREHOLDER, BENEFICIAL_OWNER, MEMBER, TRUSTEE, PARTNER, PUBLIC_OFFICER
    ownership_percent NUMERIC(6,3),
    appointed_on     DATE,
    resigned_on      DATE,
    notes            TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by       UUID,
    version          BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_party_percent CHECK (ownership_percent IS NULL OR (ownership_percent >= 0 AND ownership_percent <= 100)),
    CONSTRAINT ck_party_dates CHECK (resigned_on IS NULL OR appointed_on IS NULL OR resigned_on >= appointed_on)
);
CREATE INDEX IF NOT EXISTS idx_compliance_client_parties ON compliance_client_parties(tenant_id, client_id);
-- the same person cannot hold the same role twice at once
CREATE UNIQUE INDEX IF NOT EXISTS uq_compliance_client_party_active_role
    ON compliance_client_parties(client_id, id_number, role) WHERE id_number IS NOT NULL AND resigned_on IS NULL;

CREATE TABLE IF NOT EXISTS compliance_client_sites (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    client_id       UUID NOT NULL REFERENCES compliance_service_clients(id) ON DELETE CASCADE,
    name            VARCHAR(200) NOT NULL,
    site_type       VARCHAR(20) NOT NULL DEFAULT 'BRANCH',     -- HEAD_OFFICE, BRANCH, FACTORY, WAREHOUSE, SITE, OTHER
    address         TEXT,
    employee_count  INTEGER,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_site_employees CHECK (employee_count IS NULL OR employee_count >= 0)
);
CREATE INDEX IF NOT EXISTS idx_compliance_client_sites ON compliance_client_sites(tenant_id, client_id);
-- one head office per client
CREATE UNIQUE INDEX IF NOT EXISTS uq_compliance_client_head_office
    ON compliance_client_sites(client_id) WHERE site_type = 'HEAD_OFFICE' AND active;
