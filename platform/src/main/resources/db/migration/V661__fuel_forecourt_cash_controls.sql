-- Fuel Phase 4 (Retail cash controls, gap D02). Additive only. Defaults reproduce today's behaviour exactly:
--  * cash tolerance 5.00 (the value that was hard-coded in FuelShift), separate-closer rule OFF
--  * fleet-card tender and adjustments default to 0 on existing shifts
--  * review_status NONE on every existing shift

ALTER TABLE fuel_settings ADD COLUMN IF NOT EXISTS cash_variance_tolerance NUMERIC(10,2) NOT NULL DEFAULT 5.00;
ALTER TABLE fuel_settings ADD COLUMN IF NOT EXISTS require_separate_closer BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE fuel_settings DROP CONSTRAINT IF EXISTS chk_fuel_settings_cash_tol;
ALTER TABLE fuel_settings ADD CONSTRAINT chk_fuel_settings_cash_tol CHECK (cash_variance_tolerance >= 0);

ALTER TABLE fuel_shifts ADD COLUMN IF NOT EXISTS fleet_card_amount NUMERIC(14,2) NOT NULL DEFAULT 0;
ALTER TABLE fuel_shifts ADD COLUMN IF NOT EXISTS adjustments_amount NUMERIC(14,2) NOT NULL DEFAULT 0;
ALTER TABLE fuel_shifts ADD COLUMN IF NOT EXISTS tolerance_applied NUMERIC(10,2);
ALTER TABLE fuel_shifts ADD COLUMN IF NOT EXISTS review_status VARCHAR(14) NOT NULL DEFAULT 'NONE';
ALTER TABLE fuel_shifts ADD COLUMN IF NOT EXISTS reviewed_by UUID;
ALTER TABLE fuel_shifts ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMPTZ;
ALTER TABLE fuel_shifts ADD COLUMN IF NOT EXISTS review_note VARCHAR(300);
ALTER TABLE fuel_shifts DROP CONSTRAINT IF EXISTS chk_fuel_shift_review;
ALTER TABLE fuel_shifts ADD CONSTRAINT chk_fuel_shift_review CHECK (review_status IN ('NONE','PENDING_REVIEW','REVIEWED'));
ALTER TABLE fuel_shifts DROP CONSTRAINT IF EXISTS chk_fuel_shift_reviewed;
ALTER TABLE fuel_shifts ADD CONSTRAINT chk_fuel_shift_reviewed
    CHECK (review_status <> 'REVIEWED' OR (reviewed_by IS NOT NULL AND review_note IS NOT NULL));
ALTER TABLE fuel_shifts DROP CONSTRAINT IF EXISTS chk_fuel_shift_amounts_nonneg;
ALTER TABLE fuel_shifts ADD CONSTRAINT chk_fuel_shift_amounts_nonneg CHECK (fleet_card_amount >= 0 AND adjustments_amount >= 0);

CREATE TABLE IF NOT EXISTS fuel_shift_adjustments (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL,
    shift_id     UUID NOT NULL,
    kind         VARCHAR(10) NOT NULL,
    amount       NUMERIC(14,2) NOT NULL,
    reason       VARCHAR(300) NOT NULL,
    status       VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    recorded_by  UUID,
    recorded_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_by   UUID,
    decided_at   TIMESTAMPTZ,
    decision_note VARCHAR(300),
    version      BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_fuel_shift_adj_kind CHECK (kind IN ('REFUND','VOID','DISCOUNT')),
    CONSTRAINT chk_fuel_shift_adj_amount CHECK (amount > 0),
    CONSTRAINT chk_fuel_shift_adj_reason CHECK (length(trim(reason)) >= 5),
    CONSTRAINT chk_fuel_shift_adj_status CHECK (status IN ('PENDING','APPROVED','REJECTED','WITHDRAWN')),
    CONSTRAINT chk_fuel_shift_adj_decided CHECK (status IN ('PENDING','WITHDRAWN') OR (decided_by IS NOT NULL AND decided_at IS NOT NULL)),
    CONSTRAINT chk_fuel_shift_adj_two_person CHECK (status <> 'APPROVED' OR decided_by IS DISTINCT FROM recorded_by)
);
CREATE INDEX IF NOT EXISTS idx_fuel_shift_adj_shift ON fuel_shift_adjustments (tenant_id, shift_id);
CREATE INDEX IF NOT EXISTS idx_fuel_shift_adj_pending ON fuel_shift_adjustments (tenant_id, status) WHERE status = 'PENDING';

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'FUEL_SHIFT_SUPERVISE', 'Approve forecourt refunds, voids and discounts, and review shifts with a cash variance')
ON CONFLICT (name) DO NOTHING;
UPDATE permissions SET module_key = 'fuel', resource = 'shift', verb = 'SUPERVISE', sensitivity = 'FINANCIAL', requires_second_person = false, grantable_by = 'ADMIN'
 WHERE name = 'FUEL_SHIFT_SUPERVISE';
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ADMIN' AND p.name = 'FUEL_SHIFT_SUPERVISE'
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
