-- V315__tender_submission_profiles.sql
--
-- Reusable, named submission profiles (ADR-005 decision 7): the rules a portal or client sets for what may be submitted.
-- Every limit is optional; NULL means "not stated", which is not "unlimited". Numbers are typed in from the tender instructions, none are built in.
-- allowed_extensions is a comma-separated list of lower-case extensions without dots (for example 'pdf,xlsx'); NULL means not restricted.
-- A tender can override any of these when a package is built; the effective profile used is frozen into the package record (tender_packages.profile_snapshot).
CREATE TABLE tender_submission_profiles (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    name                  VARCHAR(200) NOT NULL,
    allowed_extensions    VARCHAR(300),
    max_file_bytes        BIGINT,
    max_total_bytes       BIGINT,
    max_file_count        INTEGER,
    zip_allowed           BOOLEAN,
    max_file_name_length  INTEGER,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by            UUID,
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_tsp_positive CHECK ((max_file_bytes IS NULL OR max_file_bytes > 0) AND (max_total_bytes IS NULL OR max_total_bytes > 0)
        AND (max_file_count IS NULL OR max_file_count > 0) AND (max_file_name_length IS NULL OR max_file_name_length > 0))
);
CREATE UNIQUE INDEX uq_tsp_tenant_name ON tender_submission_profiles(tenant_id, LOWER(name));
