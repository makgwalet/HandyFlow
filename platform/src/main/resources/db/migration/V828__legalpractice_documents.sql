-- V828: Legal Practice — matter documents with version history. Confirm the next free Flyway version (V827 is LP chunk 8).
--
-- A document belongs to one matter. Every upload is a numbered version that is kept for good: versions and the event log are
-- append-only and documents are never deleted. The file itself is held as restricted evidence (so the general evidence screens and the
-- external auditor never see it); evidence_id points at it and sha256 is the hash taken when the firm received the file.
-- A document under legal hold cannot be archived; a checked-out document can only get new versions from the person who has it.

CREATE TABLE IF NOT EXISTS lp_documents (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID         NOT NULL,
    matter_id           UUID         NOT NULL,
    client_id           UUID         NOT NULL,
    title               VARCHAR(255) NOT NULL,
    category            VARCHAR(20)  NOT NULL,
    status              VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    current_version     INTEGER      NOT NULL,
    legal_hold          BOOLEAN      NOT NULL DEFAULT FALSE,
    hold_reason         TEXT,
    hold_set_by         UUID,
    hold_set_by_name    VARCHAR(255),
    hold_set_at         TIMESTAMPTZ,
    checked_out_by      UUID,
    checked_out_by_name VARCHAR(255),
    checked_out_at      TIMESTAMPTZ,
    archived_reason     TEXT,
    created_by          UUID         NOT NULL,
    created_by_name     VARCHAR(255),
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_lp_doc_category CHECK (category IN ('CORRESPONDENCE','PLEADING','COURT_ORDER','CONTRACT','ADVICE','EVIDENCE','FILE_NOTE','OTHER')),
    CONSTRAINT ck_lp_doc_status   CHECK (status IN ('ACTIVE','ARCHIVED')),
    CONSTRAINT ck_lp_doc_current  CHECK (current_version >= 1),
    CONSTRAINT ck_lp_doc_title    CHECK (length(btrim(title)) >= 2),
    CONSTRAINT ck_lp_doc_hold     CHECK (NOT legal_hold OR (length(btrim(coalesce(hold_reason,''))) >= 10 AND hold_set_by IS NOT NULL AND hold_set_at IS NOT NULL)),
    CONSTRAINT ck_lp_doc_checkout CHECK ((checked_out_by IS NULL) = (checked_out_at IS NULL)),
    CONSTRAINT ck_lp_doc_archived CHECK (status <> 'ARCHIVED' OR (NOT legal_hold AND checked_out_by IS NULL AND length(btrim(coalesce(archived_reason,''))) >= 10))
);
CREATE INDEX IF NOT EXISTS idx_lp_doc_matter ON lp_documents (tenant_id, matter_id, status);
CREATE INDEX IF NOT EXISTS idx_lp_doc_client ON lp_documents (tenant_id, client_id);

CREATE TABLE IF NOT EXISTS lp_document_versions (
    id               UUID PRIMARY KEY,
    tenant_id        UUID         NOT NULL,
    document_id      UUID         NOT NULL REFERENCES lp_documents(id),
    version_no       INTEGER      NOT NULL,
    evidence_id      UUID         NOT NULL,
    file_name        VARCHAR(255) NOT NULL,
    content_type     VARCHAR(100) NOT NULL,
    size_bytes       BIGINT       NOT NULL,
    sha256           CHAR(64)     NOT NULL,
    change_note      TEXT,
    uploaded_by      UUID         NOT NULL,
    uploaded_by_name VARCHAR(255),
    uploaded_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_lp_docv_no UNIQUE (document_id, version_no),
    CONSTRAINT uq_lp_docv_evidence UNIQUE (evidence_id),
    CONSTRAINT ck_lp_docv_no   CHECK (version_no >= 1),
    CONSTRAINT ck_lp_docv_size CHECK (size_bytes > 0),
    CONSTRAINT ck_lp_docv_hash CHECK (sha256 ~ '^[0-9a-f]{64}$')
);
CREATE INDEX IF NOT EXISTS idx_lp_docv_doc ON lp_document_versions (tenant_id, document_id, version_no DESC);

CREATE TABLE IF NOT EXISTS lp_document_events (
    id            UUID PRIMARY KEY,
    tenant_id     UUID         NOT NULL,
    document_id   UUID         NOT NULL REFERENCES lp_documents(id),
    version_no    INTEGER,
    event_type    VARCHAR(20)  NOT NULL,
    actor_id      UUID         NOT NULL,
    actor_name    VARCHAR(255),
    detail        TEXT,
    occurred_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_lp_doce_type CHECK (event_type IN ('UPLOADED','NEW_VERSION','DOWNLOADED','VERIFIED','EDITED','CHECKED_OUT','CHECKOUT_CANCELLED','LOCK_OVERRIDDEN','HOLD_SET','HOLD_RELEASED','ARCHIVED','RESTORED'))
);
CREATE INDEX IF NOT EXISTS idx_lp_doce_doc ON lp_document_events (tenant_id, document_id, occurred_at DESC);

CREATE OR REPLACE FUNCTION lp_document_guard() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN RAISE EXCEPTION 'documents are never deleted' USING ERRCODE = 'integrity_constraint_violation'; END IF;
    IF NEW.id <> OLD.id OR NEW.tenant_id <> OLD.tenant_id OR NEW.matter_id <> OLD.matter_id OR NEW.client_id <> OLD.client_id OR NEW.created_at <> OLD.created_at THEN
        RAISE EXCEPTION 'a document cannot be moved to another matter' USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    IF NEW.current_version < OLD.current_version THEN
        RAISE EXCEPTION 'a document cannot go back to an earlier version' USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_lp_document_guard ON lp_documents;
CREATE TRIGGER trg_lp_document_guard BEFORE UPDATE OR DELETE ON lp_documents FOR EACH ROW EXECUTE FUNCTION lp_document_guard();

CREATE OR REPLACE FUNCTION lp_document_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% is append-only: % is not allowed', TG_TABLE_NAME, TG_OP USING ERRCODE = 'integrity_constraint_violation';
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_lp_docv_append_only ON lp_document_versions;
CREATE TRIGGER trg_lp_docv_append_only BEFORE UPDATE OR DELETE ON lp_document_versions FOR EACH ROW EXECUTE FUNCTION lp_document_append_only();
DROP TRIGGER IF EXISTS trg_lp_doce_append_only ON lp_document_events;
CREATE TRIGGER trg_lp_doce_append_only BEFORE UPDATE OR DELETE ON lp_document_events FOR EACH ROW EXECUTE FUNCTION lp_document_append_only();

-- Permissions -------------------------------------------------------------------------------------------------------
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'LEGALPRACTICE_DOCUMENT_WRITE',    'Upload matter documents and new versions, check them out and in, archive them'),
    (gen_random_uuid(), 'LEGALPRACTICE_DOCUMENT_HOLD',     'Place a matter document under legal hold, and release it'),
    (gen_random_uuid(), 'LEGALPRACTICE_DOCUMENT_OVERRIDE', 'Release a check-out held by someone else')
ON CONFLICT (name) DO NOTHING;

UPDATE permissions SET module_key = 'legalpractice', resource = 'document',
       verb = CASE name WHEN 'LEGALPRACTICE_DOCUMENT_WRITE' THEN 'WRITE' WHEN 'LEGALPRACTICE_DOCUMENT_HOLD' THEN 'HOLD' ELSE 'OVERRIDE' END,
       sensitivity = CASE name WHEN 'LEGALPRACTICE_DOCUMENT_WRITE' THEN 'NORMAL' ELSE 'SENSITIVE' END,
       requires_second_person = FALSE,
       grantable_by = 'ADMIN'
 WHERE name IN ('LEGALPRACTICE_DOCUMENT_WRITE', 'LEGALPRACTICE_DOCUMENT_HOLD', 'LEGALPRACTICE_DOCUMENT_OVERRIDE');

-- Whoever could manage matters (MANAGE or ADMIN) can write documents; ADMIN holders get hold and override. Narrow them in Roles.
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.id
  FROM role_permissions rp
  JOIN permissions src ON src.id = rp.permission_id AND src.name IN ('LEGALPRACTICE_MANAGE', 'LEGALPRACTICE_ADMIN')
  JOIN permissions np ON np.name = 'LEGALPRACTICE_DOCUMENT_WRITE'
 WHERE NOT EXISTS (SELECT 1 FROM role_permissions x WHERE x.role_id = rp.role_id AND x.permission_id = np.id);

INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, np.id
  FROM role_permissions rp
  JOIN permissions src ON src.id = rp.permission_id AND src.name = 'LEGALPRACTICE_ADMIN'
  JOIN permissions np ON np.name IN ('LEGALPRACTICE_DOCUMENT_HOLD', 'LEGALPRACTICE_DOCUMENT_OVERRIDE')
 WHERE NOT EXISTS (SELECT 1 FROM role_permissions x WHERE x.role_id = rp.role_id AND x.permission_id = np.id);
