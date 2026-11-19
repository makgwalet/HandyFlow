-- ============================================================================
-- Legal / Compliance — Phase 1, part 2: regulatory change management.
-- Depends on V284. Confirm the next free Flyway version before applying.
--
-- Publishing a new version of an instrument (or repealing the current one) creates a
-- CHANGE record and, per affected requirement, an IMPACT that someone must resolve.
-- Unchanged requirements carry their assessment and obligation link forward automatically;
-- historical assessments are superseded, never rewritten.
-- ============================================================================

CREATE TABLE legalcompliance_regulatory_changes (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    instrument_id     UUID NOT NULL REFERENCES legalcompliance_regulatory_instruments (id),
    instrument_title  VARCHAR(255) NOT NULL,
    from_version_id   UUID REFERENCES legalcompliance_regulatory_versions (id),
    to_version_id     UUID REFERENCES legalcompliance_regulatory_versions (id),
    from_label        VARCHAR(100),
    to_label          VARCHAR(100),
    change_kind       VARCHAR(20) NOT NULL,
    status            VARCHAR(10) NOT NULL,
    summary           TEXT,
    added_count       INTEGER NOT NULL DEFAULT 0,
    changed_count     INTEGER NOT NULL DEFAULT 0,
    removed_count     INTEGER NOT NULL DEFAULT 0,
    carried_count     INTEGER NOT NULL DEFAULT 0,
    created_by        UUID,
    created_by_name   VARCHAR(255),
    closed_at         TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_lc_change_kind   CHECK (change_kind IN ('PUBLISHED','REPEALED')),
    CONSTRAINT ck_lc_change_status CHECK (status IN ('OPEN','CLOSED'))
);

CREATE INDEX idx_lc_change_tenant ON legalcompliance_regulatory_changes (tenant_id, status, created_at DESC);

CREATE TABLE legalcompliance_regulatory_impacts (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    change_id             UUID NOT NULL REFERENCES legalcompliance_regulatory_changes (id),
    impact_type           VARCHAR(10) NOT NULL,
    old_requirement_id    UUID REFERENCES legalcompliance_regulatory_requirements (id),
    new_requirement_id    UUID REFERENCES legalcompliance_regulatory_requirements (id),
    reference             VARCHAR(100) NOT NULL,
    title                 VARCHAR(255) NOT NULL,
    diff_summary          TEXT,
    obligation_id         UUID,
    old_assessment_id     UUID,
    status                VARCHAR(10) NOT NULL,
    outcome               VARCHAR(40),
    resolution_note       TEXT,
    resolved_by           UUID,
    resolved_by_name      VARCHAR(255),
    resolved_at           TIMESTAMPTZ,
    due_date              DATE NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL,
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_lc_impact_type   CHECK (impact_type IN ('ADDED','CHANGED','REMOVED')),
    CONSTRAINT ck_lc_impact_status CHECK (status IN ('PENDING','RESOLVED'))
);

CREATE INDEX idx_lc_impact_change ON legalcompliance_regulatory_impacts (change_id);
CREATE INDEX idx_lc_impact_pending ON legalcompliance_regulatory_impacts (tenant_id, due_date) WHERE status = 'PENDING';

-- Traceability: an assessment created by carrying a decision across to an unchanged requirement.
ALTER TABLE legalcompliance_applicability_assessments
    ADD COLUMN carried_forward_from UUID REFERENCES legalcompliance_applicability_assessments (id);

INSERT INTO permissions (id, name, description)
VALUES (gen_random_uuid(), 'LEGALCOMPLIANCE_CHANGE_REVIEW', 'Review and resolve regulatory change impacts')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN' AND p.name = 'LEGALCOMPLIANCE_CHANGE_REVIEW'
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
