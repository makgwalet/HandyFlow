-- ═══════════════════════════════════════════════════════════════════════════
-- IMPORTANT: rename this file before running it — "V999" is a placeholder.
-- ═══════════════════════════════════════════════════════════════════════════
--
-- Closes a real gap flagged when scoping client-portal document upload:
-- acc_fica_documents.uploaded_by had no discriminator distinguishing a
-- staff UUID from a portal_user UUID. A staff member reviewing "who
-- uploaded this" later would have no way to tell which table that ID
-- actually belongs to, once portal users can upload too.
--
-- Every existing row predates portal upload entirely (it didn't exist
-- until this migration), so backfilling every current row to 'STAFF'
-- is safe and correct — not a guess, a fact about the timeline.

ALTER TABLE acc_fica_documents
    ADD COLUMN IF NOT EXISTS uploaded_by_type VARCHAR(15)
        CHECK (uploaded_by_type IN ('STAFF', 'PORTAL_USER') OR uploaded_by_type IS NULL);

UPDATE acc_fica_documents
SET uploaded_by_type = 'STAFF'
WHERE uploaded_by_type IS NULL;