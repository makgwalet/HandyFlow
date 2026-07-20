-- ═══════════════════════════════════════════════════════════════════════════
-- IMPORTANT: rename this file before running it — "V999" is a placeholder.
-- ═══════════════════════════════════════════════════════════════════════════
--
-- Adds file-storage columns to acc_fica_documents — closes the
-- accountant module audit's "document/attachment storage on client
-- records (FICA, POA)" gap.
--
-- acc_fica_documents already existed (V58__accountant_module.sql) with
-- a storage_key VARCHAR(500) column — clearly designed for a real
-- object-storage key (S3, etc.) once that exists, matching the same
-- storage_key pattern on acc_workpaper_files. There's no S3 available
-- in this environment yet (same situation already handled for SCM's
-- supplier invoice attachments), so this adds the columns needed to
-- store the file content directly for now, without touching or
-- repurposing storage_key — that column stays reserved and unused
-- until real object storage exists, rather than being hijacked to hold
-- something it wasn't designed for.
--
-- Also adding content_type/file_size_bytes/uploaded_by/uploaded_by_name
-- — none of which existed on this table at all, despite being needed
-- for correct content-type handling on download, the same size-cap
-- validation already used for SCM attachments, and a basic upload audit
-- trail (who uploaded this, matching the existing verified_by column's
-- own "who did this" pattern).

ALTER TABLE acc_fica_documents
    ADD COLUMN IF NOT EXISTS file_content_base64 TEXT,
    ADD COLUMN IF NOT EXISTS content_type         VARCHAR(100),
    ADD COLUMN IF NOT EXISTS file_size_bytes       BIGINT,
    ADD COLUMN IF NOT EXISTS uploaded_by           UUID,
    ADD COLUMN IF NOT EXISTS uploaded_by_name       VARCHAR(255);

-- Backs findByTenantIdAndClientId()/findSummariesByClient() — every
-- list/upload call is scoped to (tenant_id, client_id).
CREATE INDEX IF NOT EXISTS idx_acc_fica_documents_tenant_client
    ON acc_fica_documents (tenant_id, client_id);

-- Backs the expiry-reminder follow-up flagged in the accompanying code
-- comments — not built in this pass, but the index costs nothing to add
-- now against a column (expiry_date) that already exists and is already
-- populated on every row that has one.
CREATE INDEX IF NOT EXISTS idx_acc_fica_documents_expiry
    ON acc_fica_documents (expiry_date) WHERE expiry_date IS NOT NULL;