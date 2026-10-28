-- What happened to an offer after it was sent: when it lapses, and whether the candidate accepted, declined or the company withdrew it.
ALTER TABLE rec_applications ADD COLUMN IF NOT EXISTS offer_expires_on DATE;
ALTER TABLE rec_applications ADD COLUMN IF NOT EXISTS offer_response VARCHAR(20) NOT NULL DEFAULT 'NONE';
ALTER TABLE rec_applications ADD COLUMN IF NOT EXISTS offer_responded_at TIMESTAMPTZ;
ALTER TABLE rec_applications ADD COLUMN IF NOT EXISTS offer_response_note VARCHAR(500);
ALTER TABLE rec_applications ADD COLUMN IF NOT EXISTS offer_response_by UUID;
ALTER TABLE rec_applications ADD CONSTRAINT chk_rec_offer_response CHECK (offer_response IN ('NONE','ACCEPTED','DECLINED','WITHDRAWN'));

-- Offers already accepted in practice: anyone hired before this release keeps a clean record.
UPDATE rec_applications SET offer_response = 'ACCEPTED', offer_responded_at = hired_at WHERE stage = 'HIRED' AND offered_salary IS NOT NULL;

-- Every change to the terms of an offer that had already been put to the candidate, with the reason.
CREATE TABLE IF NOT EXISTS rec_offer_revisions (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    application_id UUID NOT NULL REFERENCES rec_applications(id) ON DELETE CASCADE,
    old_salary     NUMERIC(12,2),
    old_frequency  VARCHAR(30),
    old_start_date DATE,
    old_benefits   TEXT,
    reason         VARCHAR(500) NOT NULL,
    revised_by     UUID,
    revised_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_rec_offer_revisions_app ON rec_offer_revisions (application_id, revised_at);
