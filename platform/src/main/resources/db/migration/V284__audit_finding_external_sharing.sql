-- V284__audit_finding_external_sharing.sql
-- Closes the confirmed "no internal-audit engine reachable by an
-- external auditor" gap. Two separate, deliberately decoupled
-- concepts per the agreed design: finding status (already existed)
-- and external visibility (new here) -- CLOSED is a precondition for
-- sharing, checked at the domain level in AuditFinding.share(), but is
-- never itself the security mechanism. Also fixes a real, adjacent
-- gap: reopen() previously discarded closedAt with no trace a closure
-- ever happened -- closed_at/previously_closed_at/reopened_at/
-- reopen_reason close that.

ALTER TABLE audit_findings
    ADD COLUMN closed_at            TIMESTAMPTZ,
    ADD COLUMN previously_closed_at TIMESTAMPTZ,
    ADD COLUMN reopened_at          TIMESTAMPTZ,
    ADD COLUMN reopen_reason        TEXT,
    ADD COLUMN external_visibility  VARCHAR(20) NOT NULL DEFAULT 'INTERNAL_ONLY', -- INTERNAL_ONLY | SHARED | WITHDRAWN
    ADD COLUMN shared_at            TIMESTAMPTZ,
    ADD COLUMN shared_by            UUID,
    ADD COLUMN sharing_reason       TEXT;
