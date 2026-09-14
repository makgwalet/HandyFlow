-- V273__internal_audit_phase1.sql
-- Internal Audit Phase 1, per the design agreed with the product owner:
-- Audit Universe, hybrid risk scoring, Annual Audit Plan, Engagement
-- shell with engagement-scoped role assignments. See
-- InternalAuditController's own class comment for the fuller design
-- context and what's deliberately deferred to later phases
-- (workpapers, GL sampling, findings, report sign-off).

CREATE TABLE audit_universe_entries (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    name              VARCHAR(200) NOT NULL,
    description       TEXT,
    process_area      VARCHAR(100),        -- e.g. "Payroll", "Procurement", "Journal Entries" -- free text by design, not an enum, since this varies per tenant's own business
    gl_account_group  VARCHAR(100),        -- optional -- only meaningful for GL-focused universe entries
    last_audit_date   DATE,                -- feeds the system-calculated "time since last audit" risk input; null = never audited
    active            BOOLEAN NOT NULL DEFAULT true,
    created_by        UUID,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_universe_tenant ON audit_universe_entries (tenant_id) WHERE active = true;

-- FIX (agreed design): two risk fields, not one -- system_calculated_risk
-- is computed from the five V1 inputs below (equal-weighted sum, per
-- the product owner's own "don't over-engineer day one" guidance);
-- final_audit_risk is what everything downstream (the annual plan)
-- actually reads. override_reason/override_approved_by are required
-- (enforced at the service layer, not the DB) whenever the two values
-- differ -- the calculated number is an input to the auditor's
-- judgment, never a substitute for it.
CREATE TABLE audit_risk_assessments (
    id                            UUID PRIMARY KEY,
    tenant_id                     UUID NOT NULL,
    universe_entry_id             UUID NOT NULL REFERENCES audit_universe_entries (id),
    inherent_risk_score           INT NOT NULL CHECK (inherent_risk_score BETWEEN 1 AND 5),
    control_risk_score            INT NOT NULL CHECK (control_risk_score BETWEEN 1 AND 5),
    historical_findings_score     INT NOT NULL CHECK (historical_findings_score BETWEEN 1 AND 5),
    time_since_last_audit_score   INT NOT NULL CHECK (time_since_last_audit_score BETWEEN 1 AND 5),
    business_regulatory_impact_score INT NOT NULL CHECK (business_regulatory_impact_score BETWEEN 1 AND 5),
    system_calculated_risk        VARCHAR(10) NOT NULL,   -- LOW | MEDIUM | HIGH | CRITICAL
    final_audit_risk               VARCHAR(10) NOT NULL,   -- LOW | MEDIUM | HIGH | CRITICAL -- defaults to system_calculated_risk, auditor can override
    override_reason                TEXT,
    override_approved_by           UUID,
    assessed_by                    UUID,
    assessed_at                    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_risk_universe ON audit_risk_assessments (tenant_id, universe_entry_id);

CREATE TABLE audit_annual_plans (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    plan_year     INT NOT NULL,
    status        VARCHAR(15) NOT NULL DEFAULT 'DRAFT', -- DRAFT | APPROVED | ACTIVE | CLOSED
    approved_by   UUID,
    approved_at   TIMESTAMPTZ,
    created_by    UUID,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, plan_year)
);

CREATE TABLE audit_plan_entries (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    plan_id              UUID NOT NULL REFERENCES audit_annual_plans (id),
    universe_entry_id    UUID NOT NULL REFERENCES audit_universe_entries (id),
    risk_assessment_id   UUID REFERENCES audit_risk_assessments (id),
    planned_quarter      INT CHECK (planned_quarter BETWEEN 1 AND 4),
    rationale            TEXT,             -- why this entry was selected -- typically references the risk assessment, but kept as free text since a plan can also select something for other reasons (regulatory mandate, board request)
    status               VARCHAR(15) NOT NULL DEFAULT 'PLANNED', -- PLANNED | IN_PROGRESS | COMPLETED | DEFERRED
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_plan_entries_plan ON audit_plan_entries (tenant_id, plan_id);

CREATE TABLE audit_engagements (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    plan_entry_id     UUID REFERENCES audit_plan_entries (id), -- nullable -- an engagement can be ad-hoc (fraud tip-off, board request), not only plan-driven
    universe_entry_id UUID NOT NULL REFERENCES audit_universe_entries (id),
    name              VARCHAR(200) NOT NULL,
    status            VARCHAR(15) NOT NULL DEFAULT 'PLANNING', -- PLANNING | FIELDWORK | REPORTING | CLOSED
    start_date        DATE,
    end_date          DATE,
    -- Planning-detail fields (objectives/scope/materiality/audit
    -- criteria) and the full materiality model are Phase 2, per the
    -- agreed phased build -- deliberately not on this table yet.
    created_by        UUID,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_engagements_tenant ON audit_engagements (tenant_id);

-- FIX (agreed design): engagement-scoped audit responsibility,
-- deliberately separate from system permissions (AUDIT_READ/
-- AUDIT_MANAGE/AUDIT_ADMIN below) -- the same person can be Lead
-- Auditor on one engagement and Reviewer on a different one, which is
-- a per-engagement fact that can't live on a tenant-wide role.
CREATE TABLE audit_engagement_assignments (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    engagement_id  UUID NOT NULL REFERENCES audit_engagements (id),
    user_id        UUID NOT NULL,
    role           VARCHAR(30) NOT NULL, -- HEAD_OF_INTERNAL_AUDIT | AUDIT_MANAGER | SENIOR_AUDITOR | AUDITOR | AUDIT_REVIEWER
    assigned_by    UUID,
    assigned_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, engagement_id, user_id)
);
CREATE INDEX idx_audit_engagement_assignments_engagement ON audit_engagement_assignments (tenant_id, engagement_id);

-- Same 3-tier READ/MANAGE/ADMIN seeding pattern as every other module
-- this session (see V269's own precedent). ADMIN specifically for
-- approving the annual plan (a Head of Internal Audit action) --
-- everything else (universe/risk-assessment/engagement CRUD) is MANAGE.
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'AUDIT_READ',   'View the audit universe, risk assessments, annual plan, and engagements'),
    (gen_random_uuid(), 'AUDIT_MANAGE', 'Manage the audit universe, risk assessments, and engagements'),
    (gen_random_uuid(), 'AUDIT_ADMIN',  'Approve the annual audit plan')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name IN ('AUDIT_READ', 'AUDIT_MANAGE', 'AUDIT_ADMIN')
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- ── Module catalogue ──────────────────────────────────────────────────────────
-- FIX: every other module's controller gates on featureGuard.
-- requireModule(), which checks module_catalogue/tenant_modules, not
-- just permissions — added for consistency with that established
-- pattern rather than leaving this module as a silent, permission-only
-- exception. monthly_price is a genuine placeholder (0.00) -- pricing
-- for a new module is a real business decision (bundled with
-- Accounting? its own add-on tier? given this session's own earlier
-- Pack-pricing design conversation, likely wants the same kind of
-- explicit product-owner sign-off) -- not something to invent here.
-- Update before this ships to any paying tenant.
INSERT INTO module_catalogue (key, name, description, monthly_price, icon, category, sort_order, is_active)
VALUES (
    'internal-audit',
    'Internal Audit',
    'Risk-based internal audit planning: audit universe, hybrid risk scoring, annual audit plan, and engagement management. Phase 1 of a larger audit engine (workpapers, GL sampling, findings/remediation, and report sign-off are later phases).',
    0.00, 'shield-check', 'FINANCE', 200, true
) ON CONFLICT (key) DO NOTHING;
