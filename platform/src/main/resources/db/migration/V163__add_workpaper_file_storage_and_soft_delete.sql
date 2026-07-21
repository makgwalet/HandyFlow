-- ═══════════════════════════════════════════════════════════════════════════
-- IMPORTANT: rename this file before running it — "V999" is a placeholder.
-- ═══════════════════════════════════════════════════════════════════════════
--
-- Closes the accountant module audit's "larger workpaper system" gap
-- (folders + files + review workflow + audit log layer). Two additive
-- fixes to acc_workpaper_files:
--
-- 1. file_content_base64 — same "no S3 in this environment" pattern
--    already used for acc_fica_documents; storage_key exists in the
--    real schema but was never wired to anything.
--
-- 2. deleted_at — a real gap found while reading the schema, not
--    assumed: acc_workpaper_audit's own event_type CHECK constraint
--    already includes 'DELETED' and 'RESTORED' as real, anticipated
--    events, but acc_workpaper_files has no deleted_at/is_deleted
--    column at all. The schema's own audit vocabulary implies a
--    soft-delete mechanism was intended; this is the missing half of
--    it, not a new idea invented here.

ALTER TABLE acc_workpaper_files
    ADD COLUMN IF NOT EXISTS file_content_base64 TEXT,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;