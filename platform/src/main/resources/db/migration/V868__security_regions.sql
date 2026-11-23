-- Regions: a level above branches. A region groups branches; a user given a region sees every site of every branch in it,
-- including branches added to it later. Access follows the live membership, nothing is copied, so a region never leaves a user unrestricted.
CREATE TABLE IF NOT EXISTS security_regions (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL REFERENCES tenants(id),
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_security_regions PRIMARY KEY (id),
    CONSTRAINT uq_security_regions_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_security_regions_name CHECK (length(btrim(name)) > 0)
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_security_regions_name ON security_regions (tenant_id, lower(name));

-- A branch is in at most one region. The same company only (composite key), and deleting a region frees its branches.
ALTER TABLE security_branches ADD COLUMN IF NOT EXISTS region_id UUID;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_security_branches_region') THEN
        ALTER TABLE security_branches ADD CONSTRAINT fk_security_branches_region
            FOREIGN KEY (tenant_id, region_id) REFERENCES security_regions (tenant_id, id) ON DELETE NO ACTION;
    END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_security_branches_region ON security_branches (tenant_id, region_id) WHERE region_id IS NOT NULL;

-- Users limited to a region. No row for a user = no region limit. A region cannot be deleted while a user holds it
-- (so a limited user can never silently become unrestricted).
CREATE TABLE IF NOT EXISTS security_user_regions (
    tenant_id  UUID        NOT NULL REFERENCES tenants(id),
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    region_id  UUID        NOT NULL,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_security_user_regions PRIMARY KEY (user_id, region_id),
    CONSTRAINT fk_security_user_regions_region FOREIGN KEY (tenant_id, region_id) REFERENCES security_regions (tenant_id, id) ON DELETE RESTRICT
);
CREATE INDEX IF NOT EXISTS idx_security_user_regions_region ON security_user_regions (tenant_id, region_id);

-- The branch's text label always shows its region's name, however the branch is saved or the region renamed.
CREATE OR REPLACE FUNCTION hf_branch_region_label() RETURNS trigger AS $$
BEGIN
    IF NEW.region_id IS NOT NULL THEN
        SELECT r.name INTO NEW.region FROM security_regions r WHERE r.id = NEW.region_id AND r.tenant_id = NEW.tenant_id;
    END IF;
    RETURN NEW;
END $$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_branch_region_label ON security_branches;
CREATE TRIGGER trg_branch_region_label BEFORE INSERT OR UPDATE ON security_branches FOR EACH ROW EXECUTE FUNCTION hf_branch_region_label();

CREATE OR REPLACE FUNCTION hf_region_renamed() RETURNS trigger AS $$
BEGIN
    IF NEW.name IS DISTINCT FROM OLD.name THEN
        UPDATE security_branches SET region = NEW.name WHERE region_id = NEW.id AND tenant_id = NEW.tenant_id;
    END IF;
    RETURN NEW;
END $$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_region_renamed ON security_regions;
CREATE TRIGGER trg_region_renamed AFTER UPDATE ON security_regions FOR EACH ROW EXECUTE FUNCTION hf_region_renamed();

-- Carry over the free-text region already typed on branches: one region per distinct name (ignoring case and spaces).
INSERT INTO security_regions (tenant_id, name)
SELECT b.tenant_id, min(btrim(b.region))
FROM security_branches b
WHERE b.region IS NOT NULL AND btrim(b.region) <> '' AND b.region_id IS NULL
GROUP BY b.tenant_id, lower(btrim(b.region))
ON CONFLICT DO NOTHING;
UPDATE security_branches b SET region_id = r.id
FROM security_regions r
WHERE b.region_id IS NULL AND b.region IS NOT NULL AND btrim(b.region) <> '' AND r.tenant_id = b.tenant_id AND lower(r.name) = lower(btrim(b.region));

-- Row-level security (V866), if it is installed.
DO $$ BEGIN
    IF to_regproc('hf_rls_apply') IS NOT NULL THEN PERFORM hf_rls_apply(); END IF;
END $$;
