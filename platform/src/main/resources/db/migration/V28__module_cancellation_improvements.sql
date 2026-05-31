-- V28__module_cancellation_improvements.sql
-- WHY? Three gaps in module lifecycle:
-- 1. Cancellation needs a grace period (access until end of billing month)
-- 2. Re-activation after cancellation should be ACTIVE not TRIAL
-- 3. Track activation count so we know if it's a first or subsequent activation

ALTER TABLE tenant_modules ADD COLUMN IF NOT EXISTS access_until   TIMESTAMP;
ALTER TABLE tenant_modules ADD COLUMN IF NOT EXISTS activation_count INT NOT NULL DEFAULT 1;
ALTER TABLE tenant_modules ADD COLUMN IF NOT EXISTS billing_anchor  INT NOT NULL DEFAULT 1;
-- billing_anchor = day of month the subscription was first activated
-- used to calculate end-of-billing-period for grace period