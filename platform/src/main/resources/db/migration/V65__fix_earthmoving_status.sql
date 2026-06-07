-- V65__fix_earthmoving_status.sql
-- BUG-002: HIRED_OUT status missing from earthmoving_assets CHECK constraint.
--
-- WHY? EarthAssetService.updateStatus() handles the HIRED_OUT transition but
-- V49 never added it to the DB constraint. Any hire-out action throws a
-- constraint violation at the DB level, silently rolling back the transaction.
--
-- The original constraint name may vary — use IF EXISTS on both sides to be
-- safe across environments that applied a slightly different name.

ALTER TABLE earthmoving_assets
    DROP CONSTRAINT IF EXISTS chk_asset_status,
    DROP CONSTRAINT IF EXISTS earthmoving_assets_status_check;

ALTER TABLE earthmoving_assets
    ADD CONSTRAINT chk_asset_status
    CHECK (status IN (
        'AVAILABLE',
        'DEPLOYED',
        'MAINTENANCE',
        'BREAKDOWN',
        'HIRED_OUT',
        'RETIRED'
    ));