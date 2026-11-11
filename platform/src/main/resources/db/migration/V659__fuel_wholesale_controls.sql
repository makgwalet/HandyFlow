-- Fuel Phase 2 (Wholesale controls). Additive only; every default keeps today's behaviour.
--  B04 supplier approval status (off by default, existing suppliers stay APPROVED)
--  B05 purchase orders can wait for approval from the Approvals engine
--  B01 stock adjustments above a per-tenant litre threshold need a second person

ALTER TABLE fuel_settings ADD COLUMN IF NOT EXISTS require_supplier_approval BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE fuel_settings ADD COLUMN IF NOT EXISTS adjustment_threshold_litres NUMERIC(14,3);
ALTER TABLE fuel_settings DROP CONSTRAINT IF EXISTS chk_fuel_settings_adjust_threshold;
ALTER TABLE fuel_settings ADD CONSTRAINT chk_fuel_settings_adjust_threshold
    CHECK (adjustment_threshold_litres IS NULL OR adjustment_threshold_litres > 0);

ALTER TABLE fuel_suppliers ADD COLUMN IF NOT EXISTS approval_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED';
ALTER TABLE fuel_suppliers ADD COLUMN IF NOT EXISTS status_reason VARCHAR(300);
ALTER TABLE fuel_suppliers ADD COLUMN IF NOT EXISTS status_changed_by UUID;
ALTER TABLE fuel_suppliers ADD COLUMN IF NOT EXISTS status_changed_at TIMESTAMPTZ;
ALTER TABLE fuel_suppliers ADD COLUMN IF NOT EXISTS created_by UUID;
ALTER TABLE fuel_suppliers DROP CONSTRAINT IF EXISTS chk_fuel_supplier_status;
ALTER TABLE fuel_suppliers ADD CONSTRAINT chk_fuel_supplier_status
    CHECK (approval_status IN ('PENDING_APPROVAL','APPROVED','SUSPENDED'));

ALTER TABLE fuel_purchase_orders ADD COLUMN IF NOT EXISTS approval_request_id UUID;
ALTER TABLE fuel_purchase_orders ADD COLUMN IF NOT EXISTS approval_note VARCHAR(300);
ALTER TABLE fuel_purchase_orders DROP CONSTRAINT IF EXISTS chk_fuel_po_status;
ALTER TABLE fuel_purchase_orders ADD CONSTRAINT chk_fuel_po_status
    CHECK (status IN ('DRAFT','PENDING_APPROVAL','ORDERED','PART_RECEIVED','RECEIVED','CLOSED_SHORT','CANCELLED'));

CREATE TABLE IF NOT EXISTS fuel_stock_adjustment_requests (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    tank_id       UUID NOT NULL,
    litres        NUMERIC(14,3) NOT NULL,
    reason        VARCHAR(300) NOT NULL,
    status        VARCHAR(12) NOT NULL DEFAULT 'PENDING',
    requested_by  UUID,
    requested_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_by    UUID,
    decided_at    TIMESTAMPTZ,
    decision_note VARCHAR(300),
    movement_id   UUID,
    version       BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_fuel_adj_req_status CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED')),
    CONSTRAINT chk_fuel_adj_req_litres CHECK (litres <> 0)
);
CREATE INDEX IF NOT EXISTS idx_fuel_adj_req_tenant_status ON fuel_stock_adjustment_requests (tenant_id, status, requested_at DESC);

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'FUEL_SUPPLIER_APPROVE', 'Approve or suspend fuel suppliers'),
    (gen_random_uuid(), 'FUEL_STOCK_ADJUST_APPROVE', 'Approve or reject large fuel stock adjustments requested by someone else')
ON CONFLICT (name) DO NOTHING;
UPDATE permissions SET module_key = 'fuel', resource = 'supplier', verb = 'APPROVE', sensitivity = 'SENSITIVE', requires_second_person = false, grantable_by = 'ADMIN'
 WHERE name = 'FUEL_SUPPLIER_APPROVE';
UPDATE permissions SET module_key = 'fuel', resource = 'stock-adjustment', verb = 'APPROVE', sensitivity = 'FINANCIAL', requires_second_person = false, grantable_by = 'ADMIN'
 WHERE name = 'FUEL_STOCK_ADJUST_APPROVE';
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ADMIN' AND p.name IN ('FUEL_SUPPLIER_APPROVE', 'FUEL_STOCK_ADJUST_APPROVE')
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
