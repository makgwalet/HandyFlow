-- E5: reusable site records, and links from deployments to a site, a CRM customer and a Projects project.
-- The original text (site_name, client_name) stays on every deployment as a snapshot of what was typed or chosen at the
-- time; the links are added beside it and never replace it.
CREATE TABLE IF NOT EXISTS earthmoving_sites (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    name                VARCHAR(255) NOT NULL,
    address             TEXT,
    latitude            NUMERIC(9,6) CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    longitude           NUMERIC(9,6) CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    customer_id         UUID,
    project_id          UUID,
    supervisor_user_id  UUID,
    notes               TEXT,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP NOT NULL DEFAULT now(),
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_em_site_coords CHECK ((latitude IS NULL) = (longitude IS NULL))
);
-- A site exists on its own: customer and project are optional. One active site per name per company.
CREATE UNIQUE INDEX IF NOT EXISTS uq_em_sites_tenant_name ON earthmoving_sites (tenant_id, lower(btrim(name))) WHERE active;
CREATE INDEX IF NOT EXISTS idx_em_sites_tenant ON earthmoving_sites (tenant_id, active);

ALTER TABLE earthmoving_deployments ADD COLUMN IF NOT EXISTS site_id UUID REFERENCES earthmoving_sites(id);
ALTER TABLE earthmoving_deployments ADD COLUMN IF NOT EXISTS customer_id UUID;
ALTER TABLE earthmoving_deployments ADD COLUMN IF NOT EXISTS project_id UUID;
CREATE INDEX IF NOT EXISTS idx_em_deployments_site ON earthmoving_deployments (tenant_id, site_id);
CREATE INDEX IF NOT EXISTS idx_em_deployments_customer ON earthmoving_deployments (tenant_id, customer_id);
