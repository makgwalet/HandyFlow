-- Chunk 6b: stored document files, retention and legal hold, links from documents to other
-- project records, the DOCUMENT_CONTROLLER project role, and transmittal file snapshots.
-- Requires PostgreSQL 13+.

-- ── Stored files ────────────────────────────────────────────────────────────
-- A register entry is either a LINK (file_url only, as before) or a STORED FILE (evidence_id points
-- at the bytes held by the Evidence module; file_sha256 is the hash taken at upload and is checked
-- again on every download, so an approved revision cannot be quietly swapped).
ALTER TABLE project_documents
    ADD COLUMN IF NOT EXISTS evidence_id      UUID,
    ADD COLUMN IF NOT EXISTS file_sha256      VARCHAR(64),
    ADD COLUMN IF NOT EXISTS content_type     VARCHAR(150),
    ADD COLUMN IF NOT EXISTS retention_until  DATE,
    ADD COLUMN IF NOT EXISTS legal_hold       BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_project_documents_evidence
    ON project_documents (evidence_id) WHERE evidence_id IS NOT NULL;

-- ── Retention ───────────────────────────────────────────────────────────────
-- retention_until is stamped when a project is closed (closed date + app.projects.documents.retention-years).
-- Nothing is ever deleted automatically: a document past its date is only LISTED for review, and a
-- legal hold keeps it off that list.
-- Projects that were already CLOSED before this migration get 7 years from their closed date; change the
-- interval below if your policy is different BEFORE running it.
UPDATE project_documents d
SET retention_until = ((p.closed_at AT TIME ZONE 'Africa/Johannesburg')::date + INTERVAL '7 years')::date
FROM projects p
WHERE p.id = d.project_id
  AND p.status = 'CLOSED'
  AND p.closed_at IS NOT NULL
  AND d.retention_until IS NULL;

CREATE INDEX IF NOT EXISTS idx_project_documents_retention
    ON project_documents (tenant_id, retention_until)
    WHERE retention_until IS NOT NULL AND legal_hold = FALSE;

-- ── Document-controller project role ────────────────────────────────────────
ALTER TABLE project_members DROP CONSTRAINT IF EXISTS project_members_role_check;
ALTER TABLE project_members ADD CONSTRAINT project_members_role_check CHECK (role IN
    ('SPONSOR','MANAGER','PLANNER','COST_CONTROLLER','SITE_SUPERVISOR','DOCUMENT_CONTROLLER','MEMBER','VIEWER'));

-- ── Links from a document revision to other project records (evidence linkage) ──────────────
-- The link is to a specific REVISION, so it records which version was evidence for the RFI / change /
-- task at the time. Unlinking deletes the row; the document's trail keeps who linked and unlinked it.
CREATE TABLE IF NOT EXISTS project_document_links (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    project_id      UUID         NOT NULL REFERENCES projects(id),
    document_id     UUID         NOT NULL REFERENCES project_documents(id),
    link_type       VARCHAR(20)  NOT NULL,
    target_id       UUID         NOT NULL,
    note            VARCHAR(500),
    linked_by       UUID,
    linked_by_name  VARCHAR(255),
    linked_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_document_link UNIQUE (document_id, link_type, target_id),
    CONSTRAINT project_document_links_type_check CHECK (link_type IN
        ('RFI','CHANGE_ORDER','TASK','SNAG','RISK','SITE_DIARY'))
);
CREATE INDEX IF NOT EXISTS idx_document_links_target
    ON project_document_links (project_id, link_type, target_id);

-- ── Transmittals: snapshot the file as well as the name ─────────────────────
ALTER TABLE project_transmittal_items
    ADD COLUMN IF NOT EXISTS file_name    VARCHAR(300),
    ADD COLUMN IF NOT EXISTS file_sha256  VARCHAR(64);

UPDATE project_transmittal_items i
SET file_name = d.file_name, file_sha256 = d.file_sha256
FROM project_documents d
WHERE d.id = i.document_id AND i.file_name IS NULL;

CREATE INDEX IF NOT EXISTS idx_project_transmittals_project
    ON project_transmittals (tenant_id, project_id, sent_at DESC);
CREATE INDEX IF NOT EXISTS idx_document_reviews_project
    ON project_document_reviews (tenant_id, project_id, occurred_at DESC);
