-- src/main/resources/db/migration/V297__complianceservices_client_tender_snapshots.sql
--
-- The client-scoped counterpart to compliancetender's
-- tender_submission_snapshots — same frozen-JSON design, same reasoning.
-- See ClientTenderSubmissionSnapshot.java for the full Javadoc. Snapshots
-- what exists today: the tender's own fields and its requirement matrix.
-- Does NOT yet include personnel — see ClientTenderService's own Javadoc
-- for why that's a genuinely open design question here, not simply
-- unbuilt, and ClientTenderSnapshotData's own Javadoc for how the
-- snapshot shape will need to change once it's resolved.

CREATE TABLE client_tender_submission_snapshots (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    client_tender_id UUID NOT NULL REFERENCES client_tenders(id),
    snapshot_number  INTEGER NOT NULL,
    snapshot_json    JSONB NOT NULL,
    submitted_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    submitted_by     UUID
);
CREATE INDEX idx_client_tender_snapshots_tenant ON client_tender_submission_snapshots(tenant_id);
CREATE INDEX idx_client_tender_snapshots_tender ON client_tender_submission_snapshots(client_tender_id);
CREATE UNIQUE INDEX uq_client_tender_snapshots_tender_number ON client_tender_submission_snapshots(client_tender_id, snapshot_number);
