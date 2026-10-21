-- V481__catalogue_import_jobs.sql
-- Catalogue slice 6: a record of every import that was applied (never of dry runs), so there is a history to look at,
-- and failures are visible on the dashboard. The items themselves are written through the normal catalogue rules.

CREATE TABLE IF NOT EXISTS catalogue_import_jobs (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL,
    file_name   VARCHAR(255),
    mode        VARCHAR(20)  NOT NULL,
    total_rows  INT          NOT NULL DEFAULT 0,
    created     INT          NOT NULL DEFAULT 0,
    updated     INT          NOT NULL DEFAULT 0,
    unchanged   INT          NOT NULL DEFAULT 0,
    skipped     INT          NOT NULL DEFAULT 0,   -- rows with problems that were left out on purpose
    failed      INT          NOT NULL DEFAULT 0,   -- rows that passed the check but could not be saved
    problems    TEXT,                              -- the first problems, one per line: "row 7: message"
    created_by  UUID,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT pk_catalogue_import_jobs PRIMARY KEY (id),
    CONSTRAINT ck_catalogue_import_jobs_mode CHECK (mode IN ('CREATE_ONLY','UPDATE_ONLY','CREATE_OR_UPDATE'))
);
CREATE INDEX IF NOT EXISTS ix_catalogue_import_jobs_tenant ON catalogue_import_jobs (tenant_id, created_at DESC);
