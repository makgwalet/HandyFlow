-- Document Service: metadata in PostgreSQL, file content in private storage behind the FileStorageService port. A document belongs to
-- one owner (a type and an id, in one tenant) and has versions; a version is never changed once saved, and a document is archived,
-- never deleted. Documents with an expiry date feed expiry alerts.
CREATE TABLE IF NOT EXISTS documents (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    owner_type       VARCHAR(30) NOT NULL,
    owner_id         UUID NOT NULL,
    category         VARCHAR(30) NOT NULL,
    title            VARCHAR(150) NOT NULL,
    description      VARCHAR(500),
    expires_on       DATE,
    status           VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    archived_reason  VARCHAR(300),
    archived_by      UUID,
    archived_at      TIMESTAMP,
    current_version  INTEGER NOT NULL DEFAULT 1,
    created_by       UUID,
    created_at       TIMESTAMP NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_documents_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);
CREATE INDEX IF NOT EXISTS idx_documents_owner ON documents (tenant_id, owner_type, owner_id);
CREATE INDEX IF NOT EXISTS idx_documents_expiry ON documents (tenant_id, owner_type, expires_on) WHERE status = 'ACTIVE' AND expires_on IS NOT NULL;

CREATE TABLE IF NOT EXISTS document_versions (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    document_id   UUID NOT NULL REFERENCES documents(id),
    version_no    INTEGER NOT NULL CHECK (version_no >= 1),
    file_name     VARCHAR(160) NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    size_bytes    BIGINT NOT NULL CHECK (size_bytes > 0),
    sha256        VARCHAR(64) NOT NULL,
    storage_key   VARCHAR(500) NOT NULL,
    scan_status   VARCHAR(10) NOT NULL,
    scan_detail   VARCHAR(300),
    note          VARCHAR(300),
    uploaded_by   UUID,
    uploaded_at   TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_document_version UNIQUE (document_id, version_no),
    CONSTRAINT chk_document_scan CHECK (scan_status IN ('CLEAN', 'SKIPPED'))
);

CREATE OR REPLACE FUNCTION document_versions_no_update() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'A saved document version is never changed: add a new version instead.';
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_document_versions_no_update ON document_versions;
CREATE TRIGGER trg_document_versions_no_update BEFORE UPDATE ON document_versions FOR EACH ROW EXECUTE FUNCTION document_versions_no_update();

-- Earthmoving: a new alert type for documents about to expire.
ALTER TABLE earthmoving_alert_rules DROP CONSTRAINT IF EXISTS chk_em_alert_rule_type;
ALTER TABLE earthmoving_alert_rules ADD CONSTRAINT chk_em_alert_rule_type
    CHECK (alert_type IN ('SERVICE_DUE', 'BREAKDOWN', 'WORK_ORDER_OVERDUE', 'DOCUMENT_EXPIRY'));
