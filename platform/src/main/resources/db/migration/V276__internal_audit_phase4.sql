-- V276__internal_audit_phase4.sql
-- Internal Audit Phase 4, the last of four in the agreed build:
-- findings, remediation tracking, report sign-off. Report sign-off
-- itself reuses ApprovalFacade (no new table for that — it's already
-- tracked by the approvals module's own ApprovalRequest/ApprovalStep
-- tables, looked up via getLatestRequestForEntity()).

-- FIX (agreed design, §7 of the design proposal): richer than
-- ControlException on purpose -- rootCause, recommendation,
-- managementResponse (the auditee's own reply, often required by
-- policy before a finding can close), owner (accountable for
-- remediation -- not necessarily who investigated it), and dueDate
-- (so overdue findings are visible BEFORE the fact, not only after).
-- severity is its own field here too, same hard constraint as
-- AuditException -- never derived from the engagement's risk level.
CREATE TABLE audit_findings (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    engagement_id         UUID NOT NULL REFERENCES audit_engagements (id),
    source_exception_id   UUID REFERENCES audit_exceptions (id), -- nullable -- a finding can come from a failed test, or be raised directly from an auditor's own observation
    title                 VARCHAR(300) NOT NULL,
    description           TEXT NOT NULL,
    root_cause            TEXT,
    recommendation        TEXT,
    management_response   TEXT,
    severity              VARCHAR(10) NOT NULL, -- LOW | MEDIUM | HIGH | CRITICAL
    owner                 UUID, -- accountable for remediation -- not necessarily who raised or investigated it
    due_date              DATE,
    status                VARCHAR(15) NOT NULL DEFAULT 'OPEN', -- OPEN | IN_PROGRESS | RESOLVED | CLOSED
    created_by            UUID,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at           TIMESTAMPTZ
);
CREATE INDEX idx_audit_findings_engagement ON audit_findings (tenant_id, engagement_id);
