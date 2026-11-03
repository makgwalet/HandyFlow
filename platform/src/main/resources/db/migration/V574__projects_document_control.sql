-- Chunk 6: document control — document identity, review workflow, visibility, transmittals.
-- Rename with your next Flyway version number. Requires PostgreSQL 13+.

ALTER TABLE project_documents
    ADD COLUMN IF NOT EXISTS document_number   VARCHAR(50),
    ADD COLUMN IF NOT EXISTS visibility        VARCHAR(10) NOT NULL DEFAULT 'INTERNAL',   -- INTERNAL | CLIENT
    ADD COLUMN IF NOT EXISTS superseded_by     UUID,
    ADD COLUMN IF NOT EXISTS approved_by       UUID,
    ADD COLUMN IF NOT EXISTS approved_by_name  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS approved_at       TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS voided_at         TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS voided_by         UUID,
    ADD COLUMN IF NOT EXISTS void_reason       TEXT;

-- New statuses REJECTED and VOIDED. Review first: drops every CHECK on project_documents mentioning "status".
DO $$
DECLARE c RECORD;
BEGIN
    FOR c IN SELECT conname FROM pg_constraint
             WHERE conrelid = 'project_documents'::regclass AND contype = 'c'
               AND pg_get_constraintdef(oid) ILIKE '%status%'
    LOOP
        EXECUTE format('ALTER TABLE project_documents DROP CONSTRAINT %I', c.conname);
    END LOOP;
END $$;
ALTER TABLE project_documents ADD CONSTRAINT project_documents_status_check
    CHECK (status IN ('DRAFT','CURRENT','FOR_REVIEW','APPROVED','REJECTED','SUPERSEDED','VOIDED'));
ALTER TABLE project_documents ADD CONSTRAINT project_documents_visibility_check
    CHECK (visibility IN ('INTERNAL','CLIENT'));

-- ── Backfill document numbers ───────────────────────────────────────────────
-- Until now there was no document identity; the old code grouped revisions by TYPE (a bug).
-- Rows of the same project + type + title (case-insensitive) become ONE series — the closest
-- reading of what the old title-based intent was. The prefix must match DocumentRules.prefixFor().
UPDATE project_documents d
SET document_number = x.num
FROM (
    SELECT id,
           CASE document_type
               WHEN 'DRAWING' THEN 'DWG' WHEN 'RFI' THEN 'RFI' WHEN 'SUBMITTAL' THEN 'SUB'
               WHEN 'CONTRACT' THEN 'CTR' WHEN 'REPORT' THEN 'RPT' WHEN 'PHOTO' THEN 'PHO'
               ELSE 'GEN' END
           || '-' ||
           LPAD(DENSE_RANK() OVER (PARTITION BY project_id, document_type ORDER BY LOWER(title))::text, 3, '0') AS num
    FROM project_documents
) x
WHERE x.id = d.id AND d.document_number IS NULL;

-- Continue numbering above the backfilled values (same key shape the service uses: DOC:<projectId>:<type>).
INSERT INTO tenant_number_sequences (tenant_id, sequence_name, last_value, updated_at)
SELECT tenant_id, 'DOC:' || project_id::text || ':' || document_type, COUNT(DISTINCT LOWER(title)), now()
FROM project_documents
GROUP BY tenant_id, project_id, document_type
ON CONFLICT (tenant_id, sequence_name)
DO UPDATE SET last_value = GREATEST(tenant_number_sequences.last_value, EXCLUDED.last_value), updated_at = now();

-- A revision label is unique within a series (voided documents don't count). Created only if the
-- existing data allows it, so the migration never fails on legacy duplicates.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM project_documents
        WHERE revision IS NOT NULL AND status <> 'VOIDED'
        GROUP BY project_id, document_number, LOWER(revision) HAVING COUNT(*) > 1
    ) THEN
        CREATE UNIQUE INDEX IF NOT EXISTS uq_document_revision
            ON project_documents (project_id, document_number, LOWER(revision))
            WHERE revision IS NOT NULL AND status <> 'VOIDED';
    ELSE
        RAISE NOTICE 'uq_document_revision NOT created: duplicate revision labels exist within a series — clean up, then create it manually.';
    END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_project_documents_series ON project_documents (project_id, document_number);

-- ── Append-only review / lifecycle trail ────────────────────────────────────
CREATE TABLE IF NOT EXISTS project_document_reviews (
    id           UUID PRIMARY KEY,
    tenant_id    UUID         NOT NULL,
    project_id   UUID         NOT NULL REFERENCES projects(id),
    document_id  UUID         NOT NULL REFERENCES project_documents(id),
    action       VARCHAR(20)  NOT NULL,     -- UPLOADED | SUBMITTED | APPROVED | REJECTED | SUPERSEDED | VOIDED
    comment      TEXT,
    actor_id     UUID,
    actor_name   VARCHAR(255),
    occurred_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_document_reviews_doc ON project_document_reviews (document_id, occurred_at);

-- ── Transmittals: exactly which revision went to whom, and when ─────────────
CREATE TABLE IF NOT EXISTS project_transmittals (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID         NOT NULL,
    project_id              UUID         NOT NULL REFERENCES projects(id),
    transmittal_number      VARCHAR(20)  NOT NULL,
    subject                 VARCHAR(200) NOT NULL,
    recipient_name          VARCHAR(255) NOT NULL,
    recipient_organisation  VARCHAR(255),
    recipient_email         VARCHAR(255),
    purpose                 VARCHAR(30)  NOT NULL,
    notes                   TEXT,
    sent_by                 UUID,
    sent_by_name            VARCHAR(255),
    sent_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
    acknowledged_at         TIMESTAMPTZ,
    acknowledged_by         VARCHAR(255),
    CONSTRAINT uq_transmittal_number UNIQUE (project_id, transmittal_number)
);

CREATE TABLE IF NOT EXISTS project_transmittal_items (
    id               UUID PRIMARY KEY,
    transmittal_id   UUID         NOT NULL REFERENCES project_transmittals(id),
    document_id      UUID         NOT NULL REFERENCES project_documents(id),
    document_number  VARCHAR(50),                 -- snapshot: what it was called when sent
    title            VARCHAR(300) NOT NULL,
    revision         VARCHAR(20),
    status_at_send   VARCHAR(20)  NOT NULL,
    CONSTRAINT uq_transmittal_item UNIQUE (transmittal_id, document_id)
);