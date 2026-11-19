-- ============================================================================
-- Legal / Compliance — Phase 1: regulatory library, versions, requirements,
-- applicability assessments, obligation traceability.
--
-- Version number: latest in the repository at time of writing is V283.
-- Confirm against flyway_schema_history before applying.
--
-- Library tables carry a NULLABLE tenant_id:
--   NULL      = platform-curated catalogue row (read-only to tenants)
--   NOT NULL  = a tenant's own row (tenant-isolated)
-- Tenant-owned decision tables (profile, assessments) are always tenant-scoped.
-- Nothing here assumes any law applies to any tenant: applicability is an
-- explicit, justified, auditable decision.
-- ============================================================================

-- ── Instruments (laws, regulations, standards, codes, licence conditions) ──

CREATE TABLE legalcompliance_regulatory_instruments (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID,
    identifier            VARCHAR(100) NOT NULL,
    title                 VARCHAR(255) NOT NULL,
    instrument_type       VARCHAR(30)  NOT NULL,
    jurisdiction          VARCHAR(10)  NOT NULL DEFAULT 'ZA',
    jurisdiction_level    VARCHAR(20)  NOT NULL,
    authority             VARCHAR(255),
    topic                 VARCHAR(30)  NOT NULL,
    source_url            VARCHAR(500),
    description           TEXT,
    source_instrument_id  UUID REFERENCES legalcompliance_regulatory_instruments (id),
    reviewed              BOOLEAN      NOT NULL DEFAULT FALSE,
    reviewed_by           UUID,
    reviewed_by_name      VARCHAR(255),
    reviewed_at           TIMESTAMPTZ,
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by            UUID,
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    version               BIGINT       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_lc_instrument_title
    ON legalcompliance_regulatory_instruments (COALESCE(tenant_id, '00000000-0000-0000-0000-000000000000'::uuid), lower(title));
CREATE INDEX idx_lc_instrument_tenant ON legalcompliance_regulatory_instruments (tenant_id);
CREATE INDEX idx_lc_instrument_topic  ON legalcompliance_regulatory_instruments (topic);
CREATE UNIQUE INDEX uq_lc_instrument_adopted
    ON legalcompliance_regulatory_instruments (tenant_id, source_instrument_id)
    WHERE source_instrument_id IS NOT NULL AND tenant_id IS NOT NULL;

-- ── Versions (a published version is immutable) ────────────────────────────

CREATE TABLE legalcompliance_regulatory_versions (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID,
    instrument_id      UUID NOT NULL REFERENCES legalcompliance_regulatory_instruments (id),
    version_label      VARCHAR(100) NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    effective_date     DATE,
    amended_date       DATE,
    repealed_date      DATE,
    source_url         VARCHAR(500),
    summary            TEXT,
    published_at       TIMESTAMPTZ,
    published_by       UUID,
    published_by_name  VARCHAR(255),
    created_by         UUID,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_lc_version_status CHECK (status IN ('DRAFT','CURRENT','SUPERSEDED','REPEALED'))
);

CREATE INDEX idx_lc_version_instrument ON legalcompliance_regulatory_versions (instrument_id);
CREATE UNIQUE INDEX uq_lc_version_one_current ON legalcompliance_regulatory_versions (instrument_id) WHERE status = 'CURRENT';
CREATE UNIQUE INDEX uq_lc_version_one_draft   ON legalcompliance_regulatory_versions (instrument_id) WHERE status = 'DRAFT';

-- ── Requirements (belong to exactly one version) ───────────────────────────

CREATE TABLE legalcompliance_regulatory_requirements (
    id                       UUID PRIMARY KEY,
    tenant_id                UUID,
    instrument_id            UUID NOT NULL REFERENCES legalcompliance_regulatory_instruments (id),
    version_id               UUID NOT NULL REFERENCES legalcompliance_regulatory_versions (id),
    reference                VARCHAR(100) NOT NULL,
    title                    VARCHAR(255) NOT NULL,
    requirement_text         TEXT NOT NULL,
    category                 VARCHAR(30) NOT NULL,
    recurrence               VARCHAR(20) NOT NULL,
    min_employees            INTEGER,
    industries               JSONB NOT NULL DEFAULT '[]'::jsonb,
    activities               JSONB NOT NULL DEFAULT '[]'::jsonb,
    evidence_expectation     TEXT,
    previous_requirement_id  UUID REFERENCES legalcompliance_regulatory_requirements (id),
    created_by               UUID,
    created_at               TIMESTAMPTZ NOT NULL,
    updated_at               TIMESTAMPTZ NOT NULL,
    version                  BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_lc_requirement_ref ON legalcompliance_regulatory_requirements (version_id, lower(reference));
CREATE INDEX idx_lc_requirement_instrument ON legalcompliance_regulatory_requirements (instrument_id);
CREATE INDEX idx_lc_requirement_tenant ON legalcompliance_regulatory_requirements (tenant_id);

-- ── Organisation profile (inputs to the applicability SUGGESTION only) ─────

CREATE TABLE legalcompliance_org_profiles (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    industries      JSONB NOT NULL DEFAULT '[]'::jsonb,
    activities      JSONB NOT NULL DEFAULT '[]'::jsonb,
    employee_count  INTEGER,
    entity_type     VARCHAR(100),
    updated_by      UUID,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lc_org_profile_tenant UNIQUE (tenant_id)
);

-- ── Applicability assessments (append-only decisions; never rewritten) ─────

CREATE TABLE legalcompliance_applicability_assessments (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    requirement_id       UUID NOT NULL REFERENCES legalcompliance_regulatory_requirements (id),
    version_id           UUID NOT NULL REFERENCES legalcompliance_regulatory_versions (id),
    decision             VARCHAR(20) NOT NULL,
    justification        TEXT NOT NULL,
    status               VARCHAR(20) NOT NULL,
    suggestion           VARCHAR(30),
    suggestion_reasons   JSONB NOT NULL DEFAULT '[]'::jsonb,
    assessed_by          UUID,
    assessed_by_name     VARCHAR(255),
    assessed_at          TIMESTAMPTZ NOT NULL,
    decided_by           UUID,
    decided_by_name      VARCHAR(255),
    decided_at           TIMESTAMPTZ,
    decision_note        TEXT,
    superseded_at        TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL,
    version              BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_lc_assess_decision CHECK (decision IN ('APPLICABLE','NOT_APPLICABLE')),
    CONSTRAINT ck_lc_assess_status   CHECK (status IN ('PENDING_APPROVAL','APPROVED','REJECTED','SUPERSEDED'))
);

CREATE INDEX idx_lc_assess_tenant_req ON legalcompliance_applicability_assessments (tenant_id, requirement_id);
CREATE UNIQUE INDEX uq_lc_assess_one_approved ON legalcompliance_applicability_assessments (tenant_id, requirement_id) WHERE status = 'APPROVED';
CREATE UNIQUE INDEX uq_lc_assess_one_pending  ON legalcompliance_applicability_assessments (tenant_id, requirement_id) WHERE status = 'PENDING_APPROVAL';

-- ── Append-only regulatory audit trail ─────────────────────────────────────

CREATE TABLE legalcompliance_regulatory_events (
    id           UUID PRIMARY KEY,
    tenant_id    UUID,
    entity_type  VARCHAR(40) NOT NULL,
    entity_id    UUID NOT NULL,
    event_type   VARCHAR(50) NOT NULL,
    detail       TEXT,
    actor_id     UUID,
    actor_name   VARCHAR(255),
    created_at   TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_lc_reg_events_entity ON legalcompliance_regulatory_events (entity_type, entity_id, created_at DESC);
CREATE INDEX idx_lc_reg_events_tenant ON legalcompliance_regulatory_events (tenant_id, created_at DESC);

-- ── Obligation traceability (additive; existing obligations are untouched) ──

ALTER TABLE legalcompliance_regulatory_obligations
    ADD COLUMN requirement_id       UUID REFERENCES legalcompliance_regulatory_requirements (id),
    ADD COLUMN assessment_id        UUID REFERENCES legalcompliance_applicability_assessments (id),
    ADD COLUMN source_version_id    UUID REFERENCES legalcompliance_regulatory_versions (id),
    ADD COLUMN department_name      VARCHAR(150),
    ADD COLUMN evidence_requirement TEXT;

-- At most one live obligation per requirement per tenant (idempotent generation).
CREATE UNIQUE INDEX uq_lc_obligation_requirement
    ON legalcompliance_regulatory_obligations (tenant_id, requirement_id)
    WHERE requirement_id IS NOT NULL AND deleted_at IS NULL;

-- ── Platform catalogue: instrument identifiers ONLY ────────────────────────
-- Deliberately NO requirement text is seeded. These rows name the instrument so a
-- tenant can adopt it; the requirements must be authored/reviewed by a qualified
-- person before anything is presented as a complete compliance pack. Rows are
-- marked reviewed = FALSE and carry no source URL (none is asserted here).

INSERT INTO legalcompliance_regulatory_instruments
    (id, tenant_id, identifier, title, instrument_type, jurisdiction, jurisdiction_level, authority, topic,
     reviewed, active, created_at, updated_at, version)
SELECT gen_random_uuid(), NULL, v.identifier, v.title, 'ACT', 'ZA', 'NATIONAL',
       'Parliament of the Republic of South Africa', v.topic, FALSE, TRUE, now(), now(), 0
FROM (VALUES
    ('Act 4 of 2013',   'Protection of Personal Information Act',        'POPIA'),
    ('Act 2 of 2000',   'Promotion of Access to Information Act',        'POPIA'),
    ('Act 38 of 2001',  'Financial Intelligence Centre Act',             'FICA'),
    ('Act 85 of 1993',  'Occupational Health and Safety Act',            'OHS_ACT'),
    ('Act 75 of 1997',  'Basic Conditions of Employment Act',            'BCEA'),
    ('Act 71 of 2008',  'Companies Act',                                 'COMPANIES_ACT'),
    ('Act 66 of 1995',  'Labour Relations Act',                          'LABOUR'),
    ('Act 55 of 1998',  'Employment Equity Act',                         'LABOUR'),
    ('Act 97 of 1998',  'Skills Development Act',                        'LABOUR'),
    ('Act 63 of 2001',  'Unemployment Insurance Act',                    'LABOUR'),
    ('Act 130 of 1993', 'Compensation for Occupational Injuries and Diseases Act', 'LABOUR'),
    ('Act 58 of 1962',  'Income Tax Act',                                'TAX'),
    ('Act 89 of 1991',  'Value-Added Tax Act',                           'TAX'),
    ('Act 28 of 2011',  'Tax Administration Act',                        'TAX'),
    ('Act 53 of 2003',  'Broad-Based Black Economic Empowerment Act',    'BBBEE'),
    ('Act 1 of 1999',   'Public Finance Management Act',                 'PUBLIC_FINANCE'),
    ('Act 56 of 2003',  'Municipal Finance Management Act',              'PUBLIC_FINANCE'),
    ('Act 68 of 2008',  'Consumer Protection Act',                       'CONSUMER'),
    ('Act 34 of 2005',  'National Credit Act',                           'CONSUMER'),
    ('Act 25 of 2002',  'Electronic Communications and Transactions Act','OTHER')
) AS v(identifier, title, topic)
ON CONFLICT DO NOTHING;

-- ── Fine-grained permissions (work alongside LEGALCOMPLIANCE_ADMIN) ─────────

INSERT INTO permissions (id, name, description)
VALUES
    (gen_random_uuid(), 'LEGALCOMPLIANCE_REGULATION_MANAGE',  'Manage the regulatory library: instruments, versions and requirements'),
    (gen_random_uuid(), 'LEGALCOMPLIANCE_APPLICABILITY_ASSESS',  'Assess whether a requirement applies to the organisation'),
    (gen_random_uuid(), 'LEGALCOMPLIANCE_APPLICABILITY_APPROVE', 'Approve a decision that a requirement does NOT apply')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name IN ('LEGALCOMPLIANCE_REGULATION_MANAGE','LEGALCOMPLIANCE_APPLICABILITY_ASSESS','LEGALCOMPLIANCE_APPLICABILITY_APPROVE')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );