-- Warehousing: more than one warehouse per tenant. A location belongs to exactly one warehouse, and stays there.
-- Every tenant that already has a profile or locations gets one default warehouse ("MAIN", named after its profile), and its locations move into it,
-- so nothing changes for them until they add a second warehouse.
CREATE TABLE IF NOT EXISTS whse_warehouses (
    id          UUID PRIMARY KEY,
    tenant_id   UUID         NOT NULL,
    code        VARCHAR(20)  NOT NULL CHECK (code = upper(code) AND code ~ '^[A-Z0-9][A-Z0-9_-]*$'),
    name        VARCHAR(120) NOT NULL CHECK (length(btrim(name)) > 0),
    address     TEXT,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    is_default  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ,
    CONSTRAINT uq_whse_warehouse_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_whse_warehouse_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_whse_warehouse_default_active CHECK (NOT is_default OR (active AND deleted_at IS NULL))
);
-- One default per tenant.
CREATE UNIQUE INDEX IF NOT EXISTS uq_whse_warehouse_default ON whse_warehouses (tenant_id) WHERE is_default;

INSERT INTO whse_warehouses (id, tenant_id, code, name, address, is_default)
SELECT gen_random_uuid(), t.tenant_id, 'MAIN', coalesce(nullif(btrim(p.warehouse_name), ''), 'Main warehouse'), p.physical_address, TRUE
  FROM (SELECT tenant_id FROM whse_profile UNION SELECT tenant_id FROM whse_locations) t
  LEFT JOIN whse_profile p ON p.tenant_id = t.tenant_id
ON CONFLICT DO NOTHING;

ALTER TABLE whse_locations ADD COLUMN IF NOT EXISTS warehouse_id UUID;
UPDATE whse_locations l SET warehouse_id = (SELECT w.id FROM whse_warehouses w WHERE w.tenant_id = l.tenant_id AND w.is_default) WHERE l.warehouse_id IS NULL;
ALTER TABLE whse_locations ALTER COLUMN warehouse_id SET NOT NULL;
ALTER TABLE whse_locations ADD CONSTRAINT fk_whse_location_warehouse FOREIGN KEY (tenant_id, warehouse_id) REFERENCES whse_warehouses (tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_whse_locations_warehouse ON whse_locations (tenant_id, warehouse_id) WHERE deleted_at IS NULL;

-- A floor cell is per warehouse: two warehouses can both have a bay at column 0, row 0.
DROP INDEX IF EXISTS uq_whse_location_cell;
CREATE UNIQUE INDEX uq_whse_location_cell ON whse_locations (tenant_id, warehouse_id, pos_x, pos_y, COALESCE(level, 0))
    WHERE pos_x IS NOT NULL AND deleted_at IS NULL;

-- A location inserted without a warehouse goes into the tenant's default one (made on the spot when the tenant has none yet), so every code path
-- that creates locations keeps working. The warehouse of a location cannot be changed afterwards.
CREATE OR REPLACE FUNCTION whse_locations_warehouse_guard() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.warehouse_id IS NULL THEN
            SELECT id INTO NEW.warehouse_id FROM whse_warehouses WHERE tenant_id = NEW.tenant_id AND is_default;
            IF NEW.warehouse_id IS NULL THEN
                INSERT INTO whse_warehouses (id, tenant_id, code, name, is_default) VALUES (gen_random_uuid(), NEW.tenant_id, 'MAIN', 'Main warehouse', TRUE) RETURNING id INTO NEW.warehouse_id;
            END IF;
        END IF;
        RETURN NEW;
    END IF;
    IF NEW.warehouse_id IS DISTINCT FROM OLD.warehouse_id THEN
        RAISE EXCEPTION 'A location cannot be moved to another warehouse';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_whse_locations_warehouse ON whse_locations;
CREATE TRIGGER trg_whse_locations_warehouse BEFORE INSERT OR UPDATE ON whse_locations FOR EACH ROW EXECUTE FUNCTION whse_locations_warehouse_guard();
