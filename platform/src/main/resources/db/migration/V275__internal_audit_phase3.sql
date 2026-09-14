-- V275__internal_audit_phase3.sql
-- Internal Audit Phase 3, per the agreed phased build: GL sampling
-- against AccJournalEntry, testing, exceptions. See the product
-- owner's own guidance verbatim on sampling: "I'd change the concept
-- to Sampling Plan. Because sample size is only one part of sampling."
-- Every field below is deliberately from that guidance, not a
-- simplified subset — V1 ships with sampling_method fixed to
-- AUDITOR_JUDGMENT, but every field still gets captured so the
-- workpaper is defensible later, exactly as specified.

CREATE TABLE audit_sampling_plans (
    id                     UUID PRIMARY KEY,
    tenant_id              UUID NOT NULL,
    engagement_id          UUID NOT NULL REFERENCES audit_engagements (id),
    population             INTEGER NOT NULL,
    population_value       NUMERIC(16,2),
    sampling_objective     TEXT,
    sampling_method        VARCHAR(20) NOT NULL DEFAULT 'AUDITOR_JUDGMENT', -- V1: AUDITOR_JUDGMENT only. V2 (deferred): ATTRIBUTE, VARIABLE, MONETARY_UNIT
    risk_level             VARCHAR(10), -- LOW | MEDIUM | HIGH | CRITICAL -- the engagement/universe entry's own risk, referenced here for context, not recalculated
    confidence_level       NUMERIC(5,2), -- reserved for V2 statistical sampling; nullable and unused in V1's auditor-judgment method
    expected_error_rate    NUMERIC(5,2),
    tolerable_error_rate   NUMERIC(5,2),
    sample_size            INTEGER NOT NULL,
    selection_method       VARCHAR(20) NOT NULL DEFAULT 'RANDOM', -- RANDOM | SYSTEMATIC | JUDGMENTAL
    sample_period_from     DATE,
    sample_period_to       DATE,
    exclusions             TEXT,
    rationale              TEXT,
    prepared_by            UUID,
    reviewed_by            UUID,
    status                 VARCHAR(15) NOT NULL DEFAULT 'DRAFT', -- DRAFT | FINALIZED
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_sampling_plans_engagement ON audit_sampling_plans (tenant_id, engagement_id);

-- One drawn item per sampling plan. journal_entry_id references the
-- real AccJournalEntry when GL-focused (the confirmed Phase 1-4 scope)
-- -- snapshot fields (entry_number/entry_date/amount) are frozen at
-- the moment of selection, matching real audit workpaper practice: the
-- sample's own record of what it drew shouldn't silently change if the
-- underlying journal entry is later edited.
CREATE TABLE audit_sample_items (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    sampling_plan_id    UUID NOT NULL REFERENCES audit_sampling_plans (id),
    journal_entry_id    UUID NOT NULL,
    entry_number_snapshot VARCHAR(50),
    entry_date_snapshot   DATE,
    amount_snapshot       NUMERIC(16,2),
    notes               TEXT,
    selected_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_sample_items_plan ON audit_sample_items (tenant_id, sampling_plan_id);

CREATE TABLE audit_tests (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    sample_item_id   UUID NOT NULL REFERENCES audit_sample_items (id),
    procedure        TEXT NOT NULL,
    result           VARCHAR(15) NOT NULL DEFAULT 'PENDING', -- PENDING | PASS | FAIL | EXCEPTION
    notes            TEXT,
    tested_by        UUID,
    tested_at        TIMESTAMPTZ
);
CREATE INDEX idx_audit_tests_sample_item ON audit_tests (tenant_id, sample_item_id);

-- FIX (agreed design, hard constraint): severity here is deliberately
-- its own field, never derived from or conflated with the engagement's
-- riskLevel or the sampling plan's materiality -- "a fraudulent payment
-- can be high risk" even when its Rand value is immaterial, per the
-- product owner's own example. status tracks the exception's own
-- lifecycle separately from whether it eventually becomes a formal
-- Finding (Phase 4 -- promoted_to_finding_id is nullable and unused
-- until AuditFinding exists).
CREATE TABLE audit_exceptions (
    id                     UUID PRIMARY KEY,
    tenant_id              UUID NOT NULL,
    audit_test_id          UUID NOT NULL REFERENCES audit_tests (id),
    description            TEXT NOT NULL,
    severity               VARCHAR(10) NOT NULL, -- LOW | MEDIUM | HIGH | CRITICAL
    status                 VARCHAR(20) NOT NULL DEFAULT 'OPEN', -- OPEN | DISMISSED | PROMOTED_TO_FINDING
    promoted_to_finding_id UUID, -- Phase 4
    raised_by              UUID,
    raised_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolution_notes       TEXT
);
CREATE INDEX idx_audit_exceptions_test ON audit_exceptions (tenant_id, audit_test_id);
