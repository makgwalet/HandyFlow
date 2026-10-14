-- Finance F19: Bookkeeping bank rules, client queries (missing documents, questions) and handover to the accountant.

CREATE TABLE IF NOT EXISTS bk_bank_rules (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL,
    client_id          UUID NOT NULL REFERENCES bk_clients (id),
    name               VARCHAR(120) NOT NULL,
    bank_account_id    UUID,
    direction          VARCHAR(4) NOT NULL DEFAULT 'ANY',
    match_text         VARCHAR(200),
    match_field        VARCHAR(12) NOT NULL DEFAULT 'EITHER',
    min_amount         NUMERIC(15,2),
    max_amount         NUMERIC(15,2),
    priority           INTEGER NOT NULL DEFAULT 100,
    active             BOOLEAN NOT NULL DEFAULT TRUE,
    contra_account_id  UUID NOT NULL,
    created_by         UUID,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT bk_rules_dir_chk CHECK (direction IN ('IN','OUT','ANY')),
    CONSTRAINT bk_rules_field_chk CHECK (match_field IN ('DESCRIPTION','REFERENCE','EITHER'))
);
CREATE INDEX IF NOT EXISTS idx_bk_rules_client ON bk_bank_rules (tenant_id, client_id);

CREATE TABLE IF NOT EXISTS bk_client_queries (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    client_id        UUID NOT NULL REFERENCES bk_clients (id),
    transaction_id   UUID,
    kind             VARCHAR(16) NOT NULL,
    question         TEXT NOT NULL,
    status           VARCHAR(8) NOT NULL DEFAULT 'OPEN',
    answer           TEXT,
    document_ref     VARCHAR(500),
    asked_by         UUID NOT NULL,
    asked_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    answered_by      UUID,
    answered_at      TIMESTAMPTZ,
    closed_at        TIMESTAMPTZ,
    CONSTRAINT bk_queries_kind_chk CHECK (kind IN ('MISSING_DOCUMENT','QUESTION')),
    CONSTRAINT bk_queries_status_chk CHECK (status IN ('OPEN','ANSWERED','CLOSED'))
);
CREATE INDEX IF NOT EXISTS idx_bk_queries_client ON bk_client_queries (tenant_id, client_id, status);

CREATE TABLE IF NOT EXISTS bk_handovers (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL,
    client_id          UUID NOT NULL REFERENCES bk_clients (id),
    period_end         DATE NOT NULL,
    status             VARCHAR(8) NOT NULL DEFAULT 'SENT',
    open_queries       INTEGER NOT NULL DEFAULT 0,
    note               TEXT,
    sent_by            UUID NOT NULL,
    sent_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_by       UUID,
    responded_at       TIMESTAMPTZ,
    response_note      TEXT,
    CONSTRAINT bk_handovers_status_chk CHECK (status IN ('SENT','ACCEPTED','RETURNED'))
);
CREATE INDEX IF NOT EXISTS idx_bk_handovers_client ON bk_handovers (tenant_id, client_id, period_end DESC);
-- Only one handover per client can be waiting at a time.
CREATE UNIQUE INDEX IF NOT EXISTS uq_bk_handovers_open ON bk_handovers (tenant_id, client_id) WHERE status = 'SENT';
