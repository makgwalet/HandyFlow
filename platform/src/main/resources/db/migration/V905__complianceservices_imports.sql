-- Compliance Services: bringing existing clients and registrations in from a spreadsheet. A file is read and checked first (a preview that changes nothing),
-- and only an administrator's commit creates records. Existing records are never overwritten: a row that matches one is set aside as a duplicate.
-- Every batch and every row keeps what happened to it, so the result can be reconciled against the file. Nothing is deleted.

CREATE TABLE IF NOT EXISTS compliance_import_batches (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    kind           VARCHAR(20) NOT NULL,
    file_name      VARCHAR(255) NOT NULL,
    status         VARCHAR(12) NOT NULL DEFAULT 'PREVIEWED',
    total_rows     INTEGER NOT NULL,
    ok_rows        INTEGER NOT NULL,
    duplicate_rows INTEGER NOT NULL,
    rejected_rows  INTEGER NOT NULL,
    imported_rows  INTEGER NOT NULL DEFAULT 0,
    created_by     UUID NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    committed_by   UUID,
    committed_at   TIMESTAMPTZ,
    discarded_by   UUID,
    discarded_at   TIMESTAMPTZ,
    discard_note   TEXT,
    version        BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_cs_import_kind CHECK (kind IN ('CLIENTS', 'REGISTRATIONS')),
    CONSTRAINT ck_cs_import_status CHECK (status IN ('PREVIEWED', 'COMMITTED', 'DISCARDED')),
    CONSTRAINT ck_cs_import_counts CHECK (total_rows >= 0 AND ok_rows >= 0 AND duplicate_rows >= 0 AND rejected_rows >= 0 AND imported_rows >= 0
        AND ok_rows + duplicate_rows + rejected_rows = total_rows AND imported_rows <= ok_rows),
    CONSTRAINT ck_cs_import_committed CHECK ((status = 'COMMITTED') = (committed_by IS NOT NULL AND committed_at IS NOT NULL)),
    CONSTRAINT ck_cs_import_discarded CHECK ((status = 'DISCARDED') = (discarded_by IS NOT NULL AND discarded_at IS NOT NULL AND discard_note IS NOT NULL AND length(btrim(discard_note)) >= 10))
);
CREATE INDEX IF NOT EXISTS idx_cs_import_tenant ON compliance_import_batches(tenant_id, created_at DESC);

CREATE TABLE IF NOT EXISTS compliance_import_rows (
    id         UUID PRIMARY KEY,
    tenant_id  UUID NOT NULL,
    batch_id   UUID NOT NULL REFERENCES compliance_import_batches(id),
    row_number INTEGER NOT NULL,
    fields     TEXT NOT NULL,
    status     VARCHAR(10) NOT NULL,
    message    VARCHAR(500),
    target_id  UUID,
    version    BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_cs_import_row_status CHECK (status IN ('OK', 'DUPLICATE', 'REJECTED', 'IMPORTED')),
    CONSTRAINT ck_cs_import_row_message CHECK (status = 'OK' OR message IS NOT NULL),
    CONSTRAINT ck_cs_import_row_target CHECK ((status = 'IMPORTED') = (target_id IS NOT NULL)),
    CONSTRAINT uq_cs_import_row UNIQUE (batch_id, row_number)
);
CREATE INDEX IF NOT EXISTS idx_cs_import_rows_batch ON compliance_import_rows(batch_id, row_number);
