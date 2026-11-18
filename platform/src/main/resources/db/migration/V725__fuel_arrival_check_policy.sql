-- Arrival check policy: a per-company freshness window and an enforcement switch, plus the record of a supervisor's release
-- of a stale check. Additive only. Enforcement is off for existing companies so the web driver screen keeps working until
-- the company switches it on.
ALTER TABLE fuel_settings ADD COLUMN IF NOT EXISTS arrival_check_fresh_hours INT;
ALTER TABLE fuel_settings ADD COLUMN IF NOT EXISTS arrival_check_enforced BOOLEAN NOT NULL DEFAULT false;

CREATE TABLE fuel_arrival_check_releases (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL,
    check_id     UUID NOT NULL,
    delivery_id  UUID NOT NULL,
    reason_code  VARCHAR(40) NOT NULL,
    explanation  VARCHAR(1000) NOT NULL,
    released_by  UUID NOT NULL,
    released_at  TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_fuel_arrival_releases_check ON fuel_arrival_check_releases (tenant_id, check_id, released_at DESC);

-- Granted to no role. The company decides who may release a stale check and grants it explicitly.
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'FUEL_ARRIVAL_CHECK_OVERRIDE', 'Release a driver''s stale arrival check so offloading can continue, with a reason that is recorded')
ON CONFLICT (name) DO NOTHING;

UPDATE permissions
   SET module_key = 'fuel', resource = 'arrival_check', verb = 'OVERRIDE', sensitivity = 'SENSITIVE',
       requires_second_person = false, grantable_by = 'ADMIN'
 WHERE name = 'FUEL_ARRIVAL_CHECK_OVERRIDE';
