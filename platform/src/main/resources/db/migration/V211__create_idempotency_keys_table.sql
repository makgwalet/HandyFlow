-- Tracks Idempotency-Key requests so a retried POST/PUT/PATCH doesn't
-- execute business logic twice — e.g. a mobile client on a bad connection
-- retrying a timed-out "create invoice" call must get the SAME invoice
-- back, not a second one. See HandyFlow BOS Discovery doc, Section 19.1.
--
-- Scoped by (tenant_id, request_path, idempotency_key) rather than just
-- the key alone: a client-supplied key is only meaningful within one
-- tenant and one endpoint — the same key value reused across two
-- different endpoints (by a buggy client, or coincidentally) must not be
-- treated as "the same request".

CREATE TABLE idempotency_keys (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID         NOT NULL,
    request_path     VARCHAR(255) NOT NULL,
    idempotency_key  VARCHAR(255) NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'IN_PROGRESS', -- IN_PROGRESS | COMPLETED
    response_status  INTEGER,
    response_body    TEXT,
    response_content_type VARCHAR(100),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at     TIMESTAMPTZ,

    CONSTRAINT uq_idempotency_scope UNIQUE (tenant_id, request_path, idempotency_key)
);

CREATE INDEX idx_idempotency_created_at ON idempotency_keys (created_at);
-- Supports the cleanup scheduler's age-based delete (IdempotencyCleanupScheduler)
-- without a sequential scan once this table has real volume.