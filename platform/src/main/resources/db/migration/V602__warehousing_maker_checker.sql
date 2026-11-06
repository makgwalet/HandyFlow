-- Warehousing slice 3: a second person for stock adjustments and for issuing client invoices.
--
-- Two new permissions, granted to every role that already holds WAREHOUSING_ADMIN so nobody loses a capability they have today.
-- Where only one person holds the permission the existing audited sole-approver exception applies (SeparationOfDutiesChecker).

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'WAREHOUSING_ADJUST_APPROVE',  'Approve or reject a stock adjustment asked for by someone else'),
    (gen_random_uuid(), 'WAREHOUSING_BILLING_APPROVE', 'Approve or reject a client storage and handling invoice prepared by someone else')
ON CONFLICT (name) DO NOTHING;

UPDATE permissions p
   SET module_key = v.module_key, resource = v.resource, verb = v.verb,
       sensitivity = v.sensitivity, requires_second_person = v.requires_second_person, grantable_by = v.grantable_by
  FROM (VALUES
    ('WAREHOUSING_ADJUST_APPROVE',  'warehousing', 'adjustment', 'APPROVE', 'SENSITIVE', true, 'MODULE_ADMIN'),
    ('WAREHOUSING_BILLING_APPROVE', 'warehousing', 'billing',    'APPROVE', 'FINANCIAL', true, 'MODULE_ADMIN')
  ) AS v(name, module_key, resource, verb, sensitivity, requires_second_person, grantable_by)
 WHERE p.name = v.name;

INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.id
  FROM role_permissions rp
  JOIN permissions ap ON ap.id = rp.permission_id AND ap.name = 'WAREHOUSING_ADMIN'
  CROSS JOIN permissions np
 WHERE np.name IN ('WAREHOUSING_ADJUST_APPROVE', 'WAREHOUSING_BILLING_APPROVE')
ON CONFLICT DO NOTHING;

-- ── Adjustment approval ─────────────────────────────────────────────────────
-- NULL means every adjustment applies at once, as before. When set, an adjustment larger than this many units (either
-- direction) must be requested and approved by a second person. Set by the owner; not switched on by this migration.
ALTER TABLE whse_profile ADD COLUMN IF NOT EXISTS adjustment_approval_threshold_qty NUMERIC(12,3)
    CHECK (adjustment_approval_threshold_qty IS NULL OR adjustment_approval_threshold_qty >= 0);

CREATE TABLE whse_adjustment_requests (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    inventory_id    UUID NOT NULL REFERENCES whse_inventory (id),
    delta           NUMERIC(12,3) NOT NULL CHECK (delta <> 0),
    reason          VARCHAR(500) NOT NULL CHECK (length(btrim(reason)) >= 5),
    status          VARCHAR(12) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPLIED','REJECTED','CANCELLED')),
    requested_by    UUID NOT NULL,
    requested_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_by      UUID,
    decided_at      TIMESTAMPTZ,
    decision_note   VARCHAR(500),
    sole_exception  BOOLEAN NOT NULL DEFAULT FALSE,
    version         BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_whse_adj_req_open ON whse_adjustment_requests (tenant_id, requested_at) WHERE status = 'PENDING';

-- ── Invoice approval ────────────────────────────────────────────────────────
-- A generated invoice now waits in DRAFT until a second person approves it; approval issues it and posts the revenue.
-- A rejected invoice gives its accrual days back so they can be billed again.
ALTER TABLE whse_billing_invoices
    ADD COLUMN IF NOT EXISTS prepared_by    UUID,
    ADD COLUMN IF NOT EXISTS decided_by     UUID,
    ADD COLUMN IF NOT EXISTS decided_at     TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS decision_note  VARCHAR(500),
    ADD COLUMN IF NOT EXISTS sole_exception BOOLEAN NOT NULL DEFAULT FALSE;

CREATE OR REPLACE FUNCTION whse_storage_accruals_guard() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'whse_storage_accruals rows cannot be deleted';
    END IF;
    IF NEW.tenant_id <> OLD.tenant_id OR NEW.client_id <> OLD.client_id OR NEW.inventory_id <> OLD.inventory_id
       OR NEW.accrual_date <> OLD.accrual_date OR NEW.qty_on_hand <> OLD.qty_on_hand
       OR NEW.monthly_rate <> OLD.monthly_rate OR NEW.amount <> OLD.amount THEN
        RAISE EXCEPTION 'whse_storage_accruals figures are immutable';
    END IF;
    IF OLD.billing_invoice_id IS NOT NULL AND NEW.billing_invoice_id IS DISTINCT FROM OLD.billing_invoice_id THEN
        IF NEW.billing_invoice_id IS NULL AND EXISTS (
               SELECT 1 FROM whse_billing_invoices i WHERE i.id = OLD.billing_invoice_id AND i.status = 'REJECTED') THEN
            RETURN NEW;  -- released by a rejected invoice
        END IF;
        RAISE EXCEPTION 'accrual is already billed';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
