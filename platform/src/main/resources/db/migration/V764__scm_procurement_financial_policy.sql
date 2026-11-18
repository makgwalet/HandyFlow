-- V764: procurement financial control policy.
--   1. Per-tenant policy row: how budgets measure VAT, and how far a buyer may raise a price above the requisition
--      estimate before a second person must approve it. No row = application defaults (ex-VAT, 5% tolerance).
--   2. Requisition conversions keep the original estimate, the reason for any price change, the saving, and the
--      state of any price approval. A PO with a PENDING_APPROVAL price cannot be approved.
--   3. SCM_PRICE_OVERRIDE_APPROVE: approve a buyer's price increase beyond tolerance.

CREATE TABLE IF NOT EXISTS sc_procurement_policy (
    tenant_id                    UUID PRIMARY KEY,
    budget_basis                 VARCHAR(20)  NOT NULL DEFAULT 'EX_VAT',
    vat_recoverable_pct          NUMERIC(5,2) NOT NULL DEFAULT 100,
    include_non_recoverable_vat  BOOLEAN      NOT NULL DEFAULT TRUE,
    price_increase_tolerance_pct NUMERIC(5,2) NOT NULL DEFAULT 5,
    updated_by                   UUID,
    updated_by_name              VARCHAR(200),
    updated_at                   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_sc_policy_basis CHECK (budget_basis IN ('EX_VAT','INC_VAT','NET_OF_RECOVERABLE')),
    CONSTRAINT chk_sc_policy_recoverable CHECK (vat_recoverable_pct BETWEEN 0 AND 100),
    CONSTRAINT chk_sc_policy_tolerance CHECK (price_increase_tolerance_pct BETWEEN 0 AND 100)
);

ALTER TABLE sc_requisition_conversions
    ADD COLUMN IF NOT EXISTS requisition_unit_cost NUMERIC(15,2),
    ADD COLUMN IF NOT EXISTS price_status          VARCHAR(20) NOT NULL DEFAULT 'NONE',
    ADD COLUMN IF NOT EXISTS price_change_reason   VARCHAR(40),
    ADD COLUMN IF NOT EXISTS price_change_note     VARCHAR(500),
    ADD COLUMN IF NOT EXISTS quotation_ref         VARCHAR(100),
    ADD COLUMN IF NOT EXISTS saving_amount         NUMERIC(15,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS price_decided_by      UUID,
    ADD COLUMN IF NOT EXISTS price_decided_by_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS price_decided_at      TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS price_decision_note   VARCHAR(500);

-- Conversions made before this migration: the estimate is the line's estimate; no price change was recorded.
UPDATE sc_requisition_conversions c
   SET requisition_unit_cost = l.estimated_unit_cost
  FROM sc_requisition_lines l
 WHERE l.id = c.requisition_line_id AND c.requisition_unit_cost IS NULL;

ALTER TABLE sc_requisition_conversions
    ADD CONSTRAINT chk_sc_conversion_price_status CHECK (price_status IN ('NONE','PENDING_APPROVAL','APPROVED','REJECTED'));

CREATE INDEX IF NOT EXISTS idx_sc_conversions_price_pending ON sc_requisition_conversions (tenant_id, purchase_order_id)
    WHERE price_status = 'PENDING_APPROVAL';

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'SCM_PRICE_OVERRIDE_APPROVE', 'Approve a buyer price increase beyond tolerance on a purchase order')
ON CONFLICT (name) DO NOTHING;

UPDATE permissions SET module_key = 'supply_chain', resource = 'price', verb = 'OVERRIDE_APPROVE',
       sensitivity = 'FINANCIAL', requires_second_person = TRUE, grantable_by = 'ADMIN'
 WHERE name = 'SCM_PRICE_OVERRIDE_APPROVE';

INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, np.id
  FROM role_permissions rp
  JOIN permissions admin_p ON admin_p.id = rp.permission_id AND admin_p.name = 'SCM_ADMIN'
  JOIN permissions np ON np.name = 'SCM_PRICE_OVERRIDE_APPROVE'
 WHERE NOT EXISTS (SELECT 1 FROM role_permissions x WHERE x.role_id = rp.role_id AND x.permission_id = np.id);
