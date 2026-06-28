-- =============================================================================
-- V10__crm_import_jobs.sql
-- Customer CSV import job tracking
-- =============================================================================
-- WHY a jobs table instead of processing inline in the request?
--
-- A CSV with 500 customers can take 5-15 seconds to process (dedup checks,
-- phone normalisation, validation per row).  A synchronous HTTP request
-- held open that long will time out on most load balancers (typically 30s)
-- and leave the client with no feedback.
--
-- The import_jobs table lets us:
--   1. Accept the upload immediately → return 202 Accepted + a job ID
--   2. Process the CSV asynchronously in a @Async service method
--   3. Poll GET /import-jobs/{id} for status and results
--   4. Return a detailed result (N created, M skipped, K errored) when done
-- =============================================================================

CREATE TABLE IF NOT EXISTS customer_import_jobs (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
        CONSTRAINT chk_import_status CHECK (status IN ('PENDING','PROCESSING','DONE','FAILED')),
    filename        VARCHAR(255),
    total_rows      INT          NOT NULL DEFAULT 0,
    created_count   INT          NOT NULL DEFAULT 0,
    skipped_count   INT          NOT NULL DEFAULT 0,
    error_count     INT          NOT NULL DEFAULT 0,
    -- JSONB array of per-row errors: [{"row":3,"name":"X","reason":"Duplicate email"}]
    row_errors      JSONB,
    started_at      TIMESTAMP,
    completed_at    TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    created_by      UUID,

    CONSTRAINT pk_customer_import_jobs PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_import_jobs_tenant
    ON customer_import_jobs (tenant_id, created_at DESC);

COMMENT ON TABLE customer_import_jobs IS
    'Tracks async CSV import operations. Rows are written by CustomerImportService. Poll status via GET /api/v1/crm/customers/import/{id}';
