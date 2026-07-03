-- V___fix_earthmoving_asset_type.sql
-- (rename to the next available Flyway version number in your sequence,
-- e.g. V128__fix_earthmoving_asset_type.sql)
--
-- Same bug class as V65's status fix: earthmoving_assets.asset_type almost
-- certainly has a CHECK constraint from the original table migration that
-- predates COMPACTOR and DRILL being added to the frontend's ASSET_TYPES
-- list and the rest of the application code. Evidence: creating a
-- COMPACTOR or DRILL asset via the API fails with a 500 while every other
-- type (DOZER, EXCAVATOR, GRADER, LOADER, DUMPER, CRANE, ROLLER, SCRAPER,
-- OTHER) succeeds — a textbook CHECK constraint violation. If your
-- constraint isn't actually named 'chk_asset_type' or
-- 'earthmoving_assets_asset_type_check', find its real name first with:
--
--   SELECT conname FROM pg_constraint
--   WHERE conrelid = 'earthmoving_assets'::regclass AND contype = 'c';
--
-- and add that name to the DROP list below alongside the two guessed here.

ALTER TABLE earthmoving_assets
    DROP CONSTRAINT IF EXISTS chk_asset_type,
    DROP CONSTRAINT IF EXISTS earthmoving_assets_asset_type_check;

ALTER TABLE earthmoving_assets
    ADD CONSTRAINT chk_asset_type
    CHECK (asset_type IN (
        'DOZER',
        'EXCAVATOR',
        'GRADER',
        'LOADER',
        'DUMPER',
        'CRANE',
        'ROLLER',
        'SCRAPER',
        'COMPACTOR',
        'DRILL',
        'OTHER'
    ));