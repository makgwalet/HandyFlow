-- V424 (Earthmoving patch E1, 2026-10-15): fixes found in the Earthmoving audit, module permissions, status history.
--
-- 1. Maintenance types. The screen offers ELECTRICAL, HYDRAULICS, ENGINE and TRACKS but V13 allowed only six types, so
--    saving any of those four failed in the database. Widened here.
-- 2. Fleet numbers must be unique per tenant. The service comment said the database enforced this; it did not.
-- 3. One open shift per machine, enforced by the database (the service check alone lets two quick submissions both pass).
-- 4. Shift sanity checks (end after start, hours only go up, fuel not negative). NOT VALID: new rows are checked, old
--    rows are left alone, so a tenant with an old odd row can still upgrade.
-- 5. Permissions. Every Earthmoving endpoint used the generic USER_* permissions. Now:
--      EARTHMOVING_READ      view
--      EARTHMOVING_WRITE     create and edit assets, hour readings, shifts, report incidents
--      EARTHMOVING_DEPLOY    deploy, return, hire out
--      EARTHMOVING_MAINTAIN  record maintenance, resolve incidents, send to maintenance
--      EARTHMOVING_RELEASE   release a machine from maintenance or breakdown (a separate, explicit authority)
--      EARTHMOVING_ADMIN     retire, delete, settings. It does NOT include release or any safety control.
--    Existing access is carried over so nobody loses a screen on day one: holders of USER_READ get READ, USER_CREATE or
--    USER_UPDATE get WRITE and MAINTAIN, USER_UPDATE gets DEPLOY, USER_DELETE gets ADMIN. RELEASE goes only to the ADMIN
--    and OWNER roles. People need to sign in again to pick up the new permissions.
-- 6. earthmoving_status_events: every status change with who, when, why and the meter reading. Before this the reason a
--    person typed on screen was thrown away.
-- Re-runnable.

-- 1 ---------------------------------------------------------------------------------------------------------------
ALTER TABLE earthmoving_maintenance DROP CONSTRAINT IF EXISTS chk_maintenance_type;
ALTER TABLE earthmoving_maintenance ADD CONSTRAINT chk_maintenance_type CHECK (
    type IN ('SERVICE','REPAIR','INSPECTION','TYRE','BATTERY','ELECTRICAL','HYDRAULICS','ENGINE','TRACKS','OTHER'));

-- 2 and 3: only create the index when the data allows it; otherwise warn and leave the service check in place ----------
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM earthmoving_assets
                WHERE deleted_at IS NULL AND fleet_number IS NOT NULL AND fleet_number <> ''
                GROUP BY tenant_id, fleet_number HAVING COUNT(*) > 1) THEN
        RAISE WARNING 'V424: duplicate fleet numbers exist, unique index NOT created. Find them with: SELECT tenant_id, fleet_number, COUNT(*) FROM earthmoving_assets WHERE deleted_at IS NULL AND fleet_number IS NOT NULL AND fleet_number <> '''' GROUP BY 1,2 HAVING COUNT(*) > 1; then fix and create uq_earthmoving_assets_tenant_fleet_number by hand.';
    ELSE
        CREATE UNIQUE INDEX IF NOT EXISTS uq_earthmoving_assets_tenant_fleet_number
            ON earthmoving_assets (tenant_id, fleet_number)
            WHERE deleted_at IS NULL AND fleet_number IS NOT NULL AND fleet_number <> '';
    END IF;

    IF EXISTS (SELECT 1 FROM earthmoving_operator_logs WHERE ended_at IS NULL GROUP BY asset_id HAVING COUNT(*) > 1) THEN
        RAISE WARNING 'V424: machines with more than one open shift exist, unique index NOT created. Complete the extra shifts, then create uq_em_oplogs_one_open_per_asset by hand.';
    ELSE
        CREATE UNIQUE INDEX IF NOT EXISTS uq_em_oplogs_one_open_per_asset
            ON earthmoving_operator_logs (asset_id) WHERE ended_at IS NULL;
    END IF;
END $$;

-- 4 ---------------------------------------------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_em_oplog_times') THEN
        ALTER TABLE earthmoving_operator_logs ADD CONSTRAINT chk_em_oplog_times
            CHECK (ended_at IS NULL OR ended_at >= started_at) NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_em_oplog_hours') THEN
        ALTER TABLE earthmoving_operator_logs ADD CONSTRAINT chk_em_oplog_hours
            CHECK (start_hours IS NULL OR end_hours IS NULL OR end_hours >= start_hours) NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_em_oplog_fuel') THEN
        ALTER TABLE earthmoving_operator_logs ADD CONSTRAINT chk_em_oplog_fuel
            CHECK (fuel_used_litres IS NULL OR fuel_used_litres >= 0) NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_em_asset_ownership') THEN
        ALTER TABLE earthmoving_assets ADD CONSTRAINT chk_em_asset_ownership
            CHECK (ownership_type IN ('OWN','HIRED_IN','HIRED_OUT')) NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_em_asset_money') THEN
        ALTER TABLE earthmoving_assets ADD CONSTRAINT chk_em_asset_money
            CHECK ((hourly_rate IS NULL OR hourly_rate >= 0) AND (daily_rate IS NULL OR daily_rate >= 0)
               AND (purchase_price IS NULL OR purchase_price >= 0)) NOT VALID;
    END IF;
END $$;

-- 5 ---------------------------------------------------------------------------------------------------------------
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'EARTHMOVING_READ',     'View earthmoving assets, deployments, maintenance, shifts and incidents'),
    (gen_random_uuid(), 'EARTHMOVING_WRITE',    'Create and edit earthmoving assets, hour readings and shifts; report incidents'),
    (gen_random_uuid(), 'EARTHMOVING_DEPLOY',   'Deploy, return and hire out earthmoving equipment'),
    (gen_random_uuid(), 'EARTHMOVING_MAINTAIN', 'Record maintenance, send equipment to maintenance and resolve incidents'),
    (gen_random_uuid(), 'EARTHMOVING_RELEASE',  'Release equipment from maintenance or breakdown back into service'),
    (gen_random_uuid(), 'EARTHMOVING_ADMIN',    'Retire or delete earthmoving assets and configure the module; does not include release')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.id
  FROM role_permissions rp
  JOIN permissions p  ON p.id = rp.permission_id
  JOIN permissions np ON (p.name, np.name) IN (
        ('USER_READ',   'EARTHMOVING_READ'),
        ('USER_CREATE', 'EARTHMOVING_WRITE'),
        ('USER_UPDATE', 'EARTHMOVING_WRITE'),
        ('USER_UPDATE', 'EARTHMOVING_DEPLOY'),
        ('USER_CREATE', 'EARTHMOVING_MAINTAIN'),
        ('USER_UPDATE', 'EARTHMOVING_MAINTAIN'),
        ('USER_DELETE', 'EARTHMOVING_ADMIN'))
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT r.id, p.id FROM roles r JOIN permissions p ON p.name = 'EARTHMOVING_RELEASE'
 WHERE r.name IN ('ADMIN', 'OWNER')
ON CONFLICT DO NOTHING;

-- 6 ---------------------------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS earthmoving_status_events (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID NOT NULL,
    asset_id       UUID NOT NULL REFERENCES earthmoving_assets(id),
    from_status    VARCHAR(20),
    to_status      VARCHAR(20) NOT NULL,
    reason         TEXT,
    actor_user_id  UUID,
    meter_hours    NUMERIC(10,1),
    happened_at    TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_em_status_events_asset ON earthmoving_status_events (asset_id, happened_at DESC);
