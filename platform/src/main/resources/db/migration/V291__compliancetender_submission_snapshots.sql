-- src/main/resources/db/migration/V291__compliancetender_submission_snapshots.sql
--
-- The other half of "reference, don't copy": TenderPersonnel stays a live
-- reference to HR for day-to-day editing, but a specific submission needs
-- the opposite property -- a frozen answer to "what did we actually submit"
-- that survives the referenced data changing afterward. See
-- TenderSubmissionSnapshot.java's own Javadoc.

CREATE TABLE tender_submission_snapshots (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    tender_id        UUID NOT NULL REFERENCES tenders(id),
    snapshot_number  INTEGER NOT NULL,
    snapshot_json    JSONB NOT NULL,
    submitted_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    submitted_by     UUID
);
CREATE INDEX idx_tender_snapshots_tenant ON tender_submission_snapshots(tenant_id);
CREATE INDEX idx_tender_snapshots_tender ON tender_submission_snapshots(tender_id);
CREATE UNIQUE INDEX uq_tender_snapshots_tender_number ON tender_submission_snapshots(tender_id, snapshot_number);
