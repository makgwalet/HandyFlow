-- V314__tender_packages.sql
--
-- Tender submission packages (ADR-005). A package is an assembled, auditable artifact, kept apart from Evidence.
--   tender_packages       one row per BUILD (version_no 1, 2, 3 ... per tender). Rows are never updated: a rebuild is a new version.
--   tender_package_files  what is in that build, in order, each with its own hash; the package hash covers this list.
-- The bytes live behind TenderPackageStorage; storage_key is opaque and only meaningful to the storage implementation.
-- profile_snapshot is the effective submission profile (global, tenant, tender override, system ceiling) as it was when the package was built.
CREATE TABLE tender_packages (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    tender_id         UUID NOT NULL REFERENCES tenders(id),
    version_no        INTEGER NOT NULL,
    submission_ready  BOOLEAN NOT NULL,
    includes_pricing  BOOLEAN NOT NULL,
    profile_name      VARCHAR(200),
    profile_snapshot  TEXT NOT NULL,
    issues_snapshot   TEXT NOT NULL,
    package_hash      VARCHAR(64) NOT NULL,
    file_name         VARCHAR(255) NOT NULL,
    content_type      VARCHAR(100) NOT NULL,
    size_bytes        BIGINT NOT NULL,
    page_count        INTEGER,
    storage_key       VARCHAR(1000) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    created_by_name   VARCHAR(200),
    CONSTRAINT ck_tender_packages_version CHECK (version_no >= 1),
    CONSTRAINT ck_tender_packages_size CHECK (size_bytes >= 0)
);
CREATE UNIQUE INDEX uq_tender_packages_version ON tender_packages(tender_id, version_no);
CREATE INDEX idx_tender_packages_tenant ON tender_packages(tenant_id);

CREATE TABLE tender_package_files (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    package_id     UUID NOT NULL REFERENCES tender_packages(id),
    sequence_no    INTEGER NOT NULL,
    section_key    VARCHAR(60) NOT NULL,
    file_name      VARCHAR(255) NOT NULL,
    source_type    VARCHAR(20) NOT NULL,
    size_bytes     BIGINT NOT NULL,
    sha256         VARCHAR(64) NOT NULL,
    pages          INTEGER,
    evidence_id    UUID,
    CONSTRAINT ck_tender_package_files_source CHECK (source_type IN ('GENERATED', 'ATTACHED', 'ORIGINAL'))
);
CREATE UNIQUE INDEX uq_tender_package_files_seq ON tender_package_files(package_id, sequence_no);
CREATE INDEX idx_tender_package_files_tenant ON tender_package_files(tenant_id);
