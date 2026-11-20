-- ============================================================================
-- Legal / Compliance — custom reports: saved views over the standard reports. Confirm the next free Flyway version before applying (V833 is chunk 29).
--
-- A custom report never holds data and never runs SQL. It remembers which standard report it is built on, which of that report's columns to show and in what order, up to
-- ten filters, and one sort. It is applied to the standard report's own output at run time, so every rule of the standard report (audience, hidden personal data, row cap,
-- audit on export) still applies. No new permission is needed: running uses the read authorities, downloading uses LEGALCOMPLIANCE_REPORT_EXPORT, and sharing a view with
-- the rest of the company needs LEGALCOMPLIANCE_MANAGE or ADMIN.
-- ============================================================================

CREATE TABLE legalcompliance_custom_reports (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    owner_user_id    UUID NOT NULL,
    owner_name       VARCHAR(255),
    name             VARCHAR(120) NOT NULL,
    description      VARCHAR(500),
    base_key         VARCHAR(60) NOT NULL,
    columns          JSONB NOT NULL,
    filters          JSONB NOT NULL,
    sort_column      VARCHAR(120),
    sort_desc        BOOLEAN NOT NULL DEFAULT FALSE,
    shared           BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    version          BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_lc_custom_report_name CHECK (length(trim(name)) >= 3),
    CONSTRAINT ck_lc_custom_report_columns CHECK (jsonb_typeof(columns) = 'array' AND jsonb_array_length(columns) BETWEEN 1 AND 30),
    CONSTRAINT ck_lc_custom_report_filters CHECK (jsonb_typeof(filters) = 'array' AND jsonb_array_length(filters) <= 10)
);

CREATE UNIQUE INDEX uq_lc_custom_report_name ON legalcompliance_custom_reports (tenant_id, owner_user_id, lower(name));
CREATE INDEX ix_lc_custom_report_tenant ON legalcompliance_custom_reports (tenant_id, shared, owner_user_id);
