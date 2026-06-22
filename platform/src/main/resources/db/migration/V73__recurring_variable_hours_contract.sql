-- V73__recurring_variable_hours_contract.sql
-- Adds variable-hours contract support to recurring_schedules.
-- Mining machine hire: operator logs actual hours each month,
-- minimum hours clause enforced automatically.

ALTER TABLE recurring_schedules
    ADD COLUMN IF NOT EXISTS variable_hours            BOOLEAN       NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS rate_per_hour             NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS minimum_hours_per_cycle   NUMERIC(10,2),
    ADD COLUMN IF NOT EXISTS hours_vat_rate            NUMERIC(5,2)  DEFAULT 15.00,
    ADD COLUMN IF NOT EXISTS contract_start_date       TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS contract_end_date         TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS contracted_total_hours    NUMERIC(10,2),
    ADD COLUMN IF NOT EXISTS total_hours_billed        NUMERIC(10,2) NOT NULL DEFAULT 0;

COMMENT ON COLUMN recurring_schedules.variable_hours IS
    'When true, invoice quantity is logged each cycle by an operator rather than taken from template line items.';
COMMENT ON COLUMN recurring_schedules.minimum_hours_per_cycle IS
    'Minimum hours billed per cycle even if the machine worked less. Enforces take-or-pay clause.';
COMMENT ON COLUMN recurring_schedules.total_hours_billed IS
    'Running total of hours billed across all cycles. Updated each time logCycleHours is called.';
