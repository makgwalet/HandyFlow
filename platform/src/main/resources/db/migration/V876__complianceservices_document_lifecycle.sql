-- Compliance Services: a document has a life. It is uploaded, reviewed, then verified or rejected with a reason, and later
-- superseded by a newer upload or archived with a reason. Nothing that has been reviewed is deleted any more, and every
-- step is kept in an event trail. "Expired" is not stored: it follows from the expiry date.

ALTER TABLE client_compliance_documents ADD COLUMN IF NOT EXISTS status VARCHAR(14) NOT NULL DEFAULT 'UPLOADED';
ALTER TABLE client_compliance_documents ADD COLUMN IF NOT EXISTS rejected_by UUID;
ALTER TABLE client_compliance_documents ADD COLUMN IF NOT EXISTS rejected_at TIMESTAMPTZ;
ALTER TABLE client_compliance_documents ADD COLUMN IF NOT EXISTS rejection_reason TEXT;
ALTER TABLE client_compliance_documents ADD COLUMN IF NOT EXISTS archived_by UUID;
ALTER TABLE client_compliance_documents ADD COLUMN IF NOT EXISTS archived_at TIMESTAMPTZ;
ALTER TABLE client_compliance_documents ADD COLUMN IF NOT EXISTS archive_reason TEXT;
ALTER TABLE client_compliance_documents ADD COLUMN IF NOT EXISTS superseded_by_id UUID;

-- documents verified before this change keep counting
UPDATE client_compliance_documents SET status = 'VERIFIED' WHERE verified_at IS NOT NULL AND status = 'UPLOADED';

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_client_document_status') THEN
        ALTER TABLE client_compliance_documents ADD CONSTRAINT ck_client_document_status
            CHECK (status IN ('UPLOADED','UNDER_REVIEW','VERIFIED','REJECTED','SUPERSEDED','ARCHIVED'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_client_document_reasons') THEN
        ALTER TABLE client_compliance_documents ADD CONSTRAINT ck_client_document_reasons
            CHECK ((status <> 'REJECTED' OR rejection_reason IS NOT NULL)
               AND (status <> 'ARCHIVED' OR archive_reason IS NOT NULL)
               AND (status <> 'VERIFIED' OR verified_at IS NOT NULL));
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS client_compliance_document_events (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL,
    document_id  UUID NOT NULL,
    event        VARCHAR(20) NOT NULL,
    from_status  VARCHAR(14),
    to_status    VARCHAR(14) NOT NULL,
    reason       TEXT,
    actor        UUID,
    occurred_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_client_document_events ON client_compliance_document_events(tenant_id, document_id, occurred_at);
