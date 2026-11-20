-- ============================================================================
-- Legal / Compliance — scheduled report delivery. Depends on V812. Confirm the next free Flyway version before applying.
--
-- A schedule names one standard report, how often it runs, and which users of the tenant are told when it is ready. Recipients are user ids, resolved to
-- contact details at run time, so a user who has left is dropped rather than emailed. The audience (STAFF or ADMIN) is the creator's, fixed at creation: it decides
-- which reports the schedule may name.
-- ============================================================================

CREATE TABLE legalcompliance_report_schedules (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    name                  VARCHAR(255) NOT NULL,
    report_key            VARCHAR(60) NOT NULL,
    frequency             VARCHAR(10) NOT NULL,
    run_day               INTEGER NOT NULL,
    audience              VARCHAR(10) NOT NULL,
    recipient_ids         TEXT NOT NULL,
    active                BOOLEAN NOT NULL DEFAULT TRUE,
    next_run_on           DATE NOT NULL,
    last_run_on           DATE,
    last_status           VARCHAR(6) NOT NULL DEFAULT 'NEVER',
    last_row_count        INTEGER,
    last_error            VARCHAR(500),
    consecutive_failures  INTEGER NOT NULL DEFAULT 0,
    created_by            UUID,
    created_by_name       VARCHAR(255),
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL,
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_lc_rsch_freq CHECK (frequency IN ('WEEKLY','MONTHLY','QUARTERLY')),
    CONSTRAINT ck_lc_rsch_day CHECK ((frequency = 'WEEKLY' AND run_day BETWEEN 1 AND 7) OR (frequency <> 'WEEKLY' AND run_day BETWEEN 1 AND 28)),
    CONSTRAINT ck_lc_rsch_aud CHECK (audience IN ('STAFF','ADMIN')),
    CONSTRAINT ck_lc_rsch_status CHECK (last_status IN ('NEVER','OK','FAILED'))
);
CREATE INDEX idx_lc_rsch_tenant ON legalcompliance_report_schedules (tenant_id, name);
CREATE INDEX idx_lc_rsch_due ON legalcompliance_report_schedules (next_run_on) WHERE active;
