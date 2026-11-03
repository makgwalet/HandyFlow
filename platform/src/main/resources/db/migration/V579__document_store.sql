-- Document store (Secure Document Access Engine, phase 2). One row per file written through the documents module's
-- DocumentStore, whichever storage provider holds the bytes. The row records what the file must be (hash of the original,
-- hash of the exact stored bytes, size, whether and with which key it is encrypted, which provider holds it) so a nightly job
-- can prove every file is still intact, and a later move to object storage is a copy job driven by this table.
-- What a file IS can never change after it is written; only the check results and the deletion mark change. Rows are never
-- deleted: a deleted file keeps its row as a tombstone.
CREATE TABLE IF NOT EXISTS document_store_objects (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL,
    owner_module       VARCHAR(60) NOT NULL,
    storage_key        VARCHAR(500) NOT NULL,
    storage_provider   VARCHAR(30) NOT NULL,
    content_type       VARCHAR(255),
    size_bytes         BIGINT NOT NULL CHECK (size_bytes > 0),
    stored_size_bytes  BIGINT NOT NULL CHECK (stored_size_bytes > 0),
    sha256             VARCHAR(64) NOT NULL,
    stored_sha256      VARCHAR(64) NOT NULL,
    encrypted          BOOLEAN NOT NULL,
    key_id             VARCHAR(30),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    verify_status      VARCHAR(12) NOT NULL DEFAULT 'UNVERIFIED',
    verify_detail      VARCHAR(300),
    last_verified_at   TIMESTAMPTZ,
    failed_checks      INTEGER NOT NULL DEFAULT 0 CHECK (failed_checks >= 0),
    deleted_at         TIMESTAMPTZ,
    deleted_reason     VARCHAR(300),
    CONSTRAINT uq_document_store_key UNIQUE (storage_key),
    CONSTRAINT chk_document_store_status CHECK (verify_status IN ('UNVERIFIED', 'OK', 'MISSING', 'UNREADABLE', 'MISMATCH')),
    CONSTRAINT chk_document_store_key CHECK (encrypted = (key_id IS NOT NULL)),
    CONSTRAINT chk_document_store_sha CHECK (sha256 ~ '^[0-9a-f]{64}$' AND stored_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_document_store_plain CHECK (encrypted OR (sha256 = stored_sha256 AND size_bytes = stored_size_bytes))
);
CREATE INDEX IF NOT EXISTS idx_document_store_tenant ON document_store_objects (tenant_id, owner_module);
-- The nightly check takes the rows checked longest ago (never-checked first).
CREATE INDEX IF NOT EXISTS idx_document_store_due ON document_store_objects (last_verified_at NULLS FIRST) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_document_store_failed ON document_store_objects (verify_status) WHERE deleted_at IS NULL AND verify_status NOT IN ('OK', 'UNVERIFIED');

CREATE OR REPLACE FUNCTION document_store_objects_guard() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'A document store row is never deleted: it stays as the record of the file.';
    END IF;
    IF NEW.id IS DISTINCT FROM OLD.id OR NEW.tenant_id IS DISTINCT FROM OLD.tenant_id
       OR NEW.owner_module IS DISTINCT FROM OLD.owner_module OR NEW.storage_key IS DISTINCT FROM OLD.storage_key
       OR NEW.storage_provider IS DISTINCT FROM OLD.storage_provider OR NEW.content_type IS DISTINCT FROM OLD.content_type
       OR NEW.size_bytes IS DISTINCT FROM OLD.size_bytes OR NEW.stored_size_bytes IS DISTINCT FROM OLD.stored_size_bytes
       OR NEW.sha256 IS DISTINCT FROM OLD.sha256 OR NEW.stored_sha256 IS DISTINCT FROM OLD.stored_sha256
       OR NEW.encrypted IS DISTINCT FROM OLD.encrypted OR NEW.key_id IS DISTINCT FROM OLD.key_id
       OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'What a stored file is cannot change after it is written; only its check results and deletion can.';
    END IF;
    IF OLD.deleted_at IS NOT NULL AND (NEW.deleted_at IS DISTINCT FROM OLD.deleted_at OR NEW.deleted_reason IS DISTINCT FROM OLD.deleted_reason) THEN
        RAISE EXCEPTION 'A deleted stored file cannot be restored or re-deleted.';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_document_store_objects_guard ON document_store_objects;
CREATE TRIGGER trg_document_store_objects_guard BEFORE UPDATE OR DELETE ON document_store_objects
    FOR EACH ROW EXECUTE FUNCTION document_store_objects_guard();

-- One row per run of the check (nightly, or started by a platform administrator). Append-and-finish only.
CREATE TABLE IF NOT EXISTS document_store_verify_runs (
    id             UUID PRIMARY KEY,
    trigger_type   VARCHAR(10) NOT NULL CHECK (trigger_type IN ('SCHEDULED', 'MANUAL')),
    triggered_by   UUID,
    started_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at    TIMESTAMPTZ,
    checked        INTEGER NOT NULL DEFAULT 0,
    ok             INTEGER NOT NULL DEFAULT 0,
    failed         INTEGER NOT NULL DEFAULT 0,
    canary_ok      BOOLEAN,
    canary_detail  VARCHAR(300)
);
CREATE INDEX IF NOT EXISTS idx_document_store_runs_started ON document_store_verify_runs (started_at DESC);
