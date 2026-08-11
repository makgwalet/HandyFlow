-- V213__add_principal_vetting_status.sql
-- (Rename to your actual next migration number before applying.)
--
-- Closes the VettingService.updateVettingStatus() TODO: the rollup was
-- computed on every check/result but only ever logged, never persisted --
-- there was nowhere to persist it, since Principal had no matching column.
-- This mirrors Guard.screeningStatus exactly (same rollup shape: no checks
-- yet / pending / flagged / cleared), just with a name that fits a
-- principal being vetted rather than a guard being screened.

ALTER TABLE security_principals
    ADD COLUMN IF NOT EXISTS vetting_status VARCHAR(20) NOT NULL DEFAULT 'UNVETTED';

COMMENT ON COLUMN security_principals.vetting_status IS
    'Rollup of security_principal_vetting checks for this principal: UNVETTED (no checks) | PENDING (a check is outstanding) | FLAGGED (at least one HIT) | CLEARED (all checks CLEAR/INCONCLUSIVE). Recomputed by VettingService.updateVettingStatus() after every check creation/result.';