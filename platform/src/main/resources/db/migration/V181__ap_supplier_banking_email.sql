-- V__PLACEHOLDER_ap_supplier_banking_email.sql
-- RENAME: replace __PLACEHOLDER__ with your real next Flyway version.
--
-- Separate migration rather than editing the previous
-- ap_supplier_banking one directly — no confirmation that one has
-- actually been applied yet, and editing an already-applied migration
-- causes exactly the Flyway checksum mismatch this session already hit
-- once before (the AP second-approval status column, earlier). Safer to
-- always add, never edit, once a migration might have run.

ALTER TABLE ap_supplier_banking
    ADD COLUMN email VARCHAR(255);