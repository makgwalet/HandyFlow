-- Fuel Stage 0: correction path. A wrong receipt, dispatch or delivery is
-- reversed (stock restored, reason and user recorded) instead of being
-- left in place or deleted. Reversed rows stay visible for audit and are
-- excluded from usage, margin, statement and forecast calculations.
ALTER TABLE fuel_receipts   ADD COLUMN IF NOT EXISTS reversed_at TIMESTAMPTZ, ADD COLUMN IF NOT EXISTS reversed_by UUID, ADD COLUMN IF NOT EXISTS reversal_reason VARCHAR(500);
ALTER TABLE fuel_dispatches ADD COLUMN IF NOT EXISTS reversed_at TIMESTAMPTZ, ADD COLUMN IF NOT EXISTS reversed_by UUID, ADD COLUMN IF NOT EXISTS reversal_reason VARCHAR(500);
ALTER TABLE fuel_deliveries ADD COLUMN IF NOT EXISTS reversed_at TIMESTAMPTZ, ADD COLUMN IF NOT EXISTS reversed_by UUID, ADD COLUMN IF NOT EXISTS reversal_reason VARCHAR(500);

ALTER TABLE fuel_deliveries DROP CONSTRAINT IF EXISTS chk_delivery_status;
ALTER TABLE fuel_deliveries ADD CONSTRAINT chk_delivery_status
    CHECK (status IN ('SCHEDULED','IN_TRANSIT','DELIVERED','CANCELLED','REVERSED'));
