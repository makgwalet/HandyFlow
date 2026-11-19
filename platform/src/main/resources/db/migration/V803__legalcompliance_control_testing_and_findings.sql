-- ============================================================================
-- Legal / Compliance — Phase 2, part 2: control testing and assurance, plus the findings raised by failed tests.
-- Depends on V286. Confirm the next free Flyway version before applying.
--
--  * A control becomes EFFECTIVE / PARTIALLY_EFFECTIVE / INEFFECTIVE only when a test is COMPLETED (approved by a reviewer
--    who is not the tester). Nothing else writes legalcompliance_controls.operating_effectiveness.
--  * Test evidence lives in the shared evidence module (source_module 'legalcompliance', entity 'CONTROL_TEST'); the files that
--    were attached at submission are snapshotted on the test so later detaching cannot silently change what was reviewed.
--  * A failed or partial test raises exactly one finding (unique per source). A finding is closed only by someone other than
--    the person who resolved it, against a passing retest.
-- ============================================================================

ALTER TABLE legalcompliance_controls ADD COLUMN next_test_due DATE;

CREATE TABLE legalcompliance_control_tests (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    reference            VARCHAR(20) NOT NULL,
    control_id           UUID NOT NULL REFERENCES legalcompliance_controls (id),
    control_reference    VARCHAR(20) NOT NULL,
    control_title        VARCHAR(255) NOT NULL,
    test_type            VARCHAR(10) NOT NULL,
    status               VARCHAR(12) NOT NULL,
    planned_date         DATE NOT NULL,
    procedure_text       TEXT,
    expected_evidence    TEXT,
    design_rating        VARCHAR(20),
    sample_size          INTEGER,
    sample_description   TEXT,
    tester_id            UUID,
    tester_name          VARCHAR(255),
    reviewer_id          UUID,
    reviewer_name        VARCHAR(255),
    started_at           TIMESTAMPTZ,
    test_notes           TEXT,
    result               VARCHAR(10),
    submitted_at         TIMESTAMPTZ,
    evidence_snapshot    JSONB,
    reviewed_by          UUID,
    reviewed_by_name     VARCHAR(255),
    reviewed_at          TIMESTAMPTZ,
    review_note          TEXT,
    returned_count       INTEGER NOT NULL DEFAULT 0,
    retest_of_test_id    UUID REFERENCES legalcompliance_control_tests (id),
    cancel_reason        TEXT,
    created_by           UUID,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL,
    version              BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lc_test_ref UNIQUE (tenant_id, reference),
    CONSTRAINT ck_lc_test_type   CHECK (test_type IN ('SCHEDULED','AD_HOC','RETEST')),
    CONSTRAINT ck_lc_test_status CHECK (status IN ('PLANNED','IN_PROGRESS','SUBMITTED','COMPLETED','CANCELLED')),
    CONSTRAINT ck_lc_test_result CHECK (result IS NULL OR result IN ('PASS','PARTIAL','FAIL'))
);
CREATE INDEX idx_lc_test_tenant ON legalcompliance_control_tests (tenant_id, status);
CREATE INDEX idx_lc_test_control ON legalcompliance_control_tests (control_id, created_at DESC);
-- One open test per control at a time.
CREATE UNIQUE INDEX uq_lc_test_one_open ON legalcompliance_control_tests (control_id) WHERE status IN ('PLANNED','IN_PROGRESS','SUBMITTED');

CREATE TABLE legalcompliance_findings (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    reference           VARCHAR(20) NOT NULL,
    source_type         VARCHAR(20) NOT NULL,
    source_id           UUID NOT NULL,
    control_id          UUID REFERENCES legalcompliance_controls (id),
    title               VARCHAR(255) NOT NULL,
    description         TEXT NOT NULL,
    severity            VARCHAR(10) NOT NULL,
    status              VARCHAR(15) NOT NULL,
    owner_id            UUID,
    owner_name          VARCHAR(255) NOT NULL,
    due_date            DATE NOT NULL,
    remediation_plan    TEXT,
    remediation_started_at TIMESTAMPTZ,
    resolved_by         UUID,
    resolved_by_name    VARCHAR(255),
    resolved_at         TIMESTAMPTZ,
    resolution_note     TEXT,
    verified_by         UUID,
    verified_by_name    VARCHAR(255),
    verified_at         TIMESTAMPTZ,
    verification_note   TEXT,
    closing_test_id     UUID REFERENCES legalcompliance_control_tests (id),
    reopened_count      INTEGER NOT NULL DEFAULT 0,
    closed_at           TIMESTAMPTZ,
    created_by          UUID,
    created_by_name     VARCHAR(255),
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lc_finding_ref UNIQUE (tenant_id, reference),
    CONSTRAINT uq_lc_finding_source UNIQUE (tenant_id, source_type, source_id),
    CONSTRAINT ck_lc_finding_source CHECK (source_type IN ('CONTROL_TEST')),
    CONSTRAINT ck_lc_finding_sev CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT ck_lc_finding_status CHECK (status IN ('OPEN','IN_REMEDIATION','RESOLVED','CLOSED'))
);
CREATE INDEX idx_lc_finding_tenant ON legalcompliance_findings (tenant_id, status);
CREATE INDEX idx_lc_finding_due ON legalcompliance_findings (due_date) WHERE status <> 'CLOSED';

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'LEGALCOMPLIANCE_CONTROL_TEST',        'Schedule, perform and submit control tests'),
    (gen_random_uuid(), 'LEGALCOMPLIANCE_CONTROL_TEST_REVIEW', 'Review control tests (approve or return); cannot review your own'),
    (gen_random_uuid(), 'LEGALCOMPLIANCE_FINDING_MANAGE',      'Plan remediation and mark findings resolved'),
    (gen_random_uuid(), 'LEGALCOMPLIANCE_FINDING_VERIFY',      'Verify and close findings; cannot verify what you resolved')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name IN ('LEGALCOMPLIANCE_CONTROL_TEST','LEGALCOMPLIANCE_CONTROL_TEST_REVIEW','LEGALCOMPLIANCE_FINDING_MANAGE','LEGALCOMPLIANCE_FINDING_VERIFY')
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
