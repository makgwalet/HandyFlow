ALTER TABLE earthmoving_assets
    ADD COLUMN IF NOT EXISTS fleet_number      VARCHAR(50),
    ADD COLUMN IF NOT EXISTS ownership_type    VARCHAR(20)  NOT NULL DEFAULT 'OWN',
    ADD COLUMN IF NOT EXISTS hire_supplier     VARCHAR(255),
    ADD COLUMN IF NOT EXISTS hire_start_date   DATE,
    ADD COLUMN IF NOT EXISTS hire_end_date     DATE,
    ADD COLUMN IF NOT EXISTS current_site      VARCHAR(255),
    ADD COLUMN IF NOT EXISTS current_client    VARCHAR(255);

-- Index on fleet_number for fast lookup by unit number
CREATE INDEX IF NOT EXISTS idx_earthmoving_assets_fleet_number
    ON earthmoving_assets  (tenant_id, fleet_number)
    WHERE deleted_at IS NULL;

-- Index for finding all machines on a site
CREATE INDEX IF NOT EXISTS idx_earthmoving_assets_current_site
    ON earthmoving_assets  (tenant_id, current_site)
    WHERE deleted_at IS NULL AND current_site IS NOT NULL;

COMMENT ON COLUMN earthmoving_assets.fleet_number   IS 'Unit identifier within a fleet of same type, e.g. D9-001, D9-002';
COMMENT ON COLUMN earthmoving_assets.ownership_type IS 'OWN=owned asset, HIRED_IN=hired from supplier, HIRED_OUT=lent to third party';
COMMENT ON COLUMN earthmoving_assets.hire_supplier  IS 'For HIRED_IN: name of supplier/owner of machine';
COMMENT ON COLUMN earthmoving_assets.current_site   IS 'Site name where machine is currently deployed';
COMMENT ON COLUMN earthmoving_assets.current_client IS 'Client the machine is currently working for';
