-- V955: bank payment files (phase 3). One ACTIVE file per pay run; a replacement needs a reason and marks the earlier file SUPERSEDED.
-- content holds account numbers, so it is encrypted by the application (EncryptedStringConverter) and stored as TEXT.
-- Every download is recorded.
CREATE TABLE pay_bank_files (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID NOT NULL,
    pay_run_id     UUID NOT NULL REFERENCES pay_runs(id),
    file_number    INTEGER NOT NULL,
    status         VARCHAR(12) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','SUPERSEDED')),
    payment_count  INTEGER NOT NULL,
    total_amount   NUMERIC(15,2) NOT NULL,
    excluded_note  TEXT,
    reason         VARCHAR(500),
    created_by     UUID,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    content        TEXT NOT NULL,
    version        BIGINT NOT NULL DEFAULT 0,
    UNIQUE (pay_run_id, file_number)
);
CREATE UNIQUE INDEX uq_pay_bank_files_active ON pay_bank_files (pay_run_id) WHERE status = 'ACTIVE';

CREATE TABLE pay_bank_file_downloads (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID NOT NULL,
    bank_file_id   UUID NOT NULL REFERENCES pay_bank_files(id),
    downloaded_by  UUID,
    downloaded_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_pay_bank_file_downloads_file ON pay_bank_file_downloads (tenant_id, bank_file_id);
