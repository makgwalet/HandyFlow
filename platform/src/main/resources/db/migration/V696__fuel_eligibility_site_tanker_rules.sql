-- Eligibility, step 1: a delivery site can restrict which tankers may serve it. Nothing is restricted until a site has a rule, so existing
-- companies see no change. Tanker specifications are optional; a rule that needs a specification the tanker does not have raises a Review, not a Block.
ALTER TABLE fuel_tankers
    ADD COLUMN tanker_type VARCHAR(40),
    ADD COLUMN gvm_kg      INTEGER,
    ADD COLUMN length_m    NUMERIC(5,2),
    ADD COLUMN width_m     NUMERIC(5,2),
    ADD COLUMN height_m    NUMERIC(5,2),
    ADD CONSTRAINT ck_fuel_tankers_specs CHECK ((gvm_kg IS NULL OR gvm_kg > 0) AND (length_m IS NULL OR length_m > 0) AND (width_m IS NULL OR width_m > 0) AND (height_m IS NULL OR height_m > 0));

CREATE TABLE fuel_site_access_rules (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    site_id              UUID NOT NULL,
    max_capacity_litres  NUMERIC(10,2),
    max_gvm_kg           INTEGER,
    max_length_m         NUMERIC(5,2),
    max_width_m          NUMERIC(5,2),
    max_height_m         NUMERIC(5,2),
    approved_types       VARCHAR(300),
    access_notes         VARCHAR(500),
    updated_by           UUID,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    version              BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_fuel_site_rule_limits CHECK ((max_capacity_litres IS NULL OR max_capacity_litres > 0) AND (max_gvm_kg IS NULL OR max_gvm_kg > 0)
        AND (max_length_m IS NULL OR max_length_m > 0) AND (max_width_m IS NULL OR max_width_m > 0) AND (max_height_m IS NULL OR max_height_m > 0))
);
CREATE UNIQUE INDEX uq_fuel_site_access_rules_site ON fuel_site_access_rules (tenant_id, site_id);

-- When a site lists tankers, only those may serve it.
CREATE TABLE fuel_site_approved_tankers (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    site_id     UUID NOT NULL,
    tanker_id   UUID NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_fuel_site_approved_tankers ON fuel_site_approved_tankers (tenant_id, site_id, tanker_id);

-- Audit of every load allocated with review points acknowledged. Append only.
CREATE TABLE fuel_eligibility_overrides (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    delivery_id         UUID NOT NULL,
    tanker_id           UUID NOT NULL,
    driver_employee_id  UUID NOT NULL,
    findings            VARCHAR(2000) NOT NULL,
    reason              VARCHAR(300) NOT NULL,
    approved_by         UUID NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_fuel_elig_overrides_delivery ON fuel_eligibility_overrides (tenant_id, delivery_id);

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'FUEL_ELIGIBILITY_REVIEW', 'Allocate a load that has eligibility review points (missing information), with a reason that is recorded')
ON CONFLICT (name) DO NOTHING;

UPDATE permissions
   SET module_key = 'fuel', resource = 'eligibility', verb = 'REVIEW', sensitivity = 'SENSITIVE',
       requires_second_person = false, grantable_by = 'ADMIN'
 WHERE name = 'FUEL_ELIGIBILITY_REVIEW';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name = 'FUEL_ELIGIBILITY_REVIEW'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
