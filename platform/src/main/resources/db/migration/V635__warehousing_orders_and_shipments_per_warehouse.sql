-- Warehousing: every inbound shipment and outbound order belongs to one warehouse, so counts, overdue lists and "today" figures can be cut per warehouse.
-- Existing documents go to the tenant's default warehouse (made on the spot for a tenant that has none), exactly where their stock already is
-- for a single-warehouse tenant. Nothing about stock, movements or billing changes.

-- The tenant's default warehouse; created when the tenant has none yet.
CREATE OR REPLACE FUNCTION whse_default_warehouse(p_tenant UUID) RETURNS UUID AS $$
DECLARE w UUID;
BEGIN
    SELECT id INTO w FROM whse_warehouses WHERE tenant_id = p_tenant AND is_default;
    IF w IS NULL THEN
        INSERT INTO whse_warehouses (id, tenant_id, code, name, is_default)
        VALUES (gen_random_uuid(), p_tenant, 'MAIN', 'Main warehouse', TRUE)
        ON CONFLICT (tenant_id, code) DO NOTHING;
        SELECT id INTO w FROM whse_warehouses WHERE tenant_id = p_tenant AND is_default;
        IF w IS NULL THEN
            SELECT id INTO w FROM whse_warehouses WHERE tenant_id = p_tenant AND active AND deleted_at IS NULL ORDER BY created_at LIMIT 1;
        END IF;
    END IF;
    RETURN w;
END;
$$ LANGUAGE plpgsql;

ALTER TABLE whse_inbound_shipments ADD COLUMN IF NOT EXISTS warehouse_id UUID;
ALTER TABLE whse_outbound_orders   ADD COLUMN IF NOT EXISTS warehouse_id UUID;

UPDATE whse_inbound_shipments SET warehouse_id = whse_default_warehouse(tenant_id) WHERE warehouse_id IS NULL;
UPDATE whse_outbound_orders   SET warehouse_id = whse_default_warehouse(tenant_id) WHERE warehouse_id IS NULL;

ALTER TABLE whse_inbound_shipments ALTER COLUMN warehouse_id SET NOT NULL;
ALTER TABLE whse_outbound_orders   ALTER COLUMN warehouse_id SET NOT NULL;
ALTER TABLE whse_inbound_shipments ADD CONSTRAINT fk_whse_inbound_warehouse FOREIGN KEY (tenant_id, warehouse_id) REFERENCES whse_warehouses (tenant_id, id);
ALTER TABLE whse_outbound_orders   ADD CONSTRAINT fk_whse_outbound_warehouse FOREIGN KEY (tenant_id, warehouse_id) REFERENCES whse_warehouses (tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_whse_inbound_warehouse  ON whse_inbound_shipments (tenant_id, warehouse_id, status);
CREATE INDEX IF NOT EXISTS idx_whse_outbound_warehouse ON whse_outbound_orders (tenant_id, warehouse_id, status);

-- A document inserted without a warehouse (every existing code path) goes to the tenant's default one. The warehouse can be changed only while the
-- document has had no effect on stock: an order still PENDING, a shipment still EXPECTED.
CREATE OR REPLACE FUNCTION whse_document_warehouse_guard() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.warehouse_id IS NULL THEN NEW.warehouse_id := whse_default_warehouse(NEW.tenant_id); END IF;
        RETURN NEW;
    END IF;
    IF NEW.warehouse_id IS DISTINCT FROM OLD.warehouse_id AND OLD.status NOT IN ('PENDING', 'EXPECTED') THEN
        RAISE EXCEPTION 'The warehouse of a document that has started cannot be changed';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_whse_inbound_warehouse ON whse_inbound_shipments;
CREATE TRIGGER trg_whse_inbound_warehouse BEFORE INSERT OR UPDATE ON whse_inbound_shipments FOR EACH ROW EXECUTE FUNCTION whse_document_warehouse_guard();
DROP TRIGGER IF EXISTS trg_whse_outbound_warehouse ON whse_outbound_orders;
CREATE TRIGGER trg_whse_outbound_warehouse BEFORE INSERT OR UPDATE ON whse_outbound_orders FOR EACH ROW EXECUTE FUNCTION whse_document_warehouse_guard();
