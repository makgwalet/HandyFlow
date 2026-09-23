-- src/main/resources/db/migration/V288__compliancetender_phase1.sql
--
-- Phase 1 of the compliancetender module (see STRATEGIC-ROADMAP-BACKLOG.md,
-- Part 6, for the full scoping). Compliance tracking, document vault, and
-- expiry calendar only — no tender workspace yet (later phase, deliberately
-- not built here). Four tables, matching the domain model sketch in the
-- scoping doc.

-- ── compliance_registrations ────────────────────────────────────────────────
-- One row per (tenant, authority, registration) — e.g. the tenant's own
-- CIPC company registration, SARS income tax registration, PSIRA business
-- registration, CSD supplier registration, cidb grading, NHBRC registration.

CREATE TABLE compliance_registrations (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    authority             VARCHAR(20) NOT NULL,   -- CIPC, SARS, UIF, PSIRA, CSD, CIDB, NHBRC, OTHER
    registration_type     VARCHAR(60) NOT NULL,   -- e.g. 'Income Tax', 'VAT', 'Business Registration', 'Grade 4GB'
    registration_number   VARCHAR(100),
    status                VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, EXPIRED, LAPSED, PENDING, NOT_APPLICABLE
    issued_date           DATE,
    expiry_date           DATE,                   -- nullable: some registrations (e.g. CIPC company reg) don't expire
    notes                 TEXT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by            UUID,
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_compliance_reg_expiry_after_issue
        CHECK (expiry_date IS NULL OR issued_date IS NULL OR expiry_date >= issued_date)
);
CREATE INDEX idx_compliance_reg_tenant ON compliance_registrations(tenant_id);
CREATE INDEX idx_compliance_reg_expiry_sweep ON compliance_registrations(expiry_date) WHERE status = 'ACTIVE';
-- One registration of a given authority+type per tenant — prevents an
-- accidental duplicate "CIPC / Business Registration" row for the same
-- tenant; a tenant that genuinely needs more than one (rare) can still use
-- registration_number to distinguish, this just stops the common accident.
CREATE UNIQUE INDEX uq_compliance_reg_tenant_authority_type
    ON compliance_registrations(tenant_id, authority, registration_type);

-- ── compliance_documents ────────────────────────────────────────────────────
-- Deliberately a thin wrapper referencing evidence.application.EvidenceFacade
-- (confirmed already the reusable document-vault engine, with three existing
-- consumers) — evidence_id points at what EvidenceFacade.attach(...) returns.
-- No file bytes or storage path live in this table; that's evidence's job.

CREATE TABLE compliance_documents (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    registration_id       UUID REFERENCES compliance_registrations(id), -- nullable: some documents aren't tied to one registration
    document_type         VARCHAR(60) NOT NULL,   -- e.g. 'Director ID', 'Tax Clearance Certificate', 'cidb Certificate'
    evidence_id           UUID NOT NULL,           -- FK by convention, not a DB constraint — evidence lives in another module's table
    issue_date            DATE,
    expiry_date           DATE,
    verified_by           UUID,
    verified_at           TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by            UUID,
    version               BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_compliance_doc_tenant ON compliance_documents(tenant_id);
CREATE INDEX idx_compliance_doc_registration ON compliance_documents(registration_id);
CREATE INDEX idx_compliance_doc_expiry_sweep ON compliance_documents(expiry_date);

-- ── compliance_requirements ─────────────────────────────────────────────────
-- Versioned, reusable requirement definitions (e.g. CSD_ACTIVE, CIDB_GRADE,
-- TAX_COMPLIANCE). Tenant-scoped for Phase 1, deliberately: a shared,
-- cross-tenant default catalogue (so every tenant doesn't have to define
-- "valid CSD registration" themselves) is a real, separate design question
-- — a two-tier global/tenant-override model — left for a later phase rather
-- than half-built here. Each tenant creates their own for now, even though
-- that means some early duplication across tenants.

CREATE TABLE compliance_requirements (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    code                  VARCHAR(60) NOT NULL,   -- e.g. 'CSD_ACTIVE', 'CIDB_GRADE', 'TAX_COMPLIANCE'
    name                  VARCHAR(255) NOT NULL,
    applies_to            VARCHAR(60),             -- free text for Phase 1, e.g. 'Government Tender', 'Construction'
    evidence_type         VARCHAR(60),             -- expected compliance_documents.document_type this requirement is satisfied by
    required              BOOLEAN NOT NULL DEFAULT TRUE,
    requirement_version   INTEGER NOT NULL DEFAULT 1,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by            UUID
);
CREATE INDEX idx_compliance_req_tenant ON compliance_requirements(tenant_id);
CREATE UNIQUE INDEX uq_compliance_req_tenant_code_version
    ON compliance_requirements(tenant_id, code, requirement_version);

-- ── compliance_deadlines ────────────────────────────────────────────────────
-- Feeds the expiry engine / compliance calendar. Deliberately separate from
-- compliance_registrations' own expiry_date: a registration has (at most)
-- one expiry, but a tenant can have other compliance deadlines that aren't
-- tied to any one registration (e.g. an annual return filing date, a
-- recurring declaration).

CREATE TABLE compliance_deadlines (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    registration_id       UUID REFERENCES compliance_registrations(id), -- nullable
    deadline_type         VARCHAR(60) NOT NULL,   -- e.g. 'RENEWAL', 'ANNUAL_RETURN', 'DECLARATION'
    description           VARCHAR(500),
    due_date              DATE NOT NULL,
    status                VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, DONE, MISSED
    completed_at          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by            UUID
);
CREATE INDEX idx_compliance_deadline_tenant ON compliance_deadlines(tenant_id);
CREATE INDEX idx_compliance_deadline_due_sweep ON compliance_deadlines(due_date) WHERE status = 'PENDING';

-- ── Module catalogue + permissions ──────────────────────────────────────────

INSERT INTO module_catalogue (key, name, description, category, monthly_price, is_active)
VALUES ('compliancetender', 'Business Compliance & Tender',
        'Track CIPC, SARS, UIF, PSIRA, CSD, cidb and NHBRC registrations, manage compliance documents, and monitor upcoming deadlines and renewals.',
        'Compliance', 299.00, TRUE)
ON CONFLICT (key) DO NOTHING;

INSERT INTO permissions (id, name, description, is_read_only)
VALUES
    (gen_random_uuid(), 'COMPLIANCE_READ',   'View compliance registrations, documents and deadlines', TRUE),
    (gen_random_uuid(), 'COMPLIANCE_MANAGE', 'Create and update compliance registrations, documents and deadlines', FALSE),
    (gen_random_uuid(), 'COMPLIANCE_ADMIN',  'Delete compliance records', FALSE)
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
AND p.name IN ('COMPLIANCE_READ', 'COMPLIANCE_MANAGE', 'COMPLIANCE_ADMIN')
AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
);
