-- Accounting listener for claim money events (patch 0185). Every scheme payment and write-off on a claim is recorded here as it
-- happens, with whether it has reached the general ledger. A scheme payment stays PENDING until someone says which bank account it landed in
-- (the clinic cannot know; guessing a bank would put real money in the wrong place). A write-off is posted straight away; FAILED means
-- the attempt failed and the reason says why. PENDING and FAILED rows are the ones still to post.
-- event_key makes a redelivered event harmless: the same event is only ever recorded once.

CREATE TABLE IF NOT EXISTS clinic_money_posting (
    id               UUID PRIMARY KEY,
    tenant_id        UUID           NOT NULL REFERENCES tenants(id),
    claim_id         UUID           NOT NULL,
    event_type       VARCHAR(30)    NOT NULL CHECK (event_type IN ('SCHEME_PAYMENT_RECEIVED','CLAIM_WRITTEN_OFF')),
    amount           NUMERIC(12,2)  NOT NULL CHECK (amount > 0),
    reference        VARCHAR(100),
    occurred_on      DATE           NOT NULL,
    event_key        VARCHAR(160)   NOT NULL,
    status           VARCHAR(10)    NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','POSTED','FAILED')),
    reason           VARCHAR(300),
    bank_account_id  UUID,
    journal_entry_id UUID,
    created_at       TIMESTAMP      NOT NULL DEFAULT now(),
    posted_at        TIMESTAMP,
    posted_by        UUID,
    CONSTRAINT uq_clinic_money_posting_event UNIQUE (tenant_id, event_key),
    CONSTRAINT chk_clinic_money_posted CHECK (status <> 'POSTED' OR (journal_entry_id IS NOT NULL AND posted_at IS NOT NULL))
);
CREATE INDEX IF NOT EXISTS idx_clinic_money_posting_open ON clinic_money_posting (tenant_id, status, created_at)
    WHERE status <> 'POSTED';
CREATE INDEX IF NOT EXISTS idx_clinic_money_posting_claim ON clinic_money_posting (tenant_id, claim_id);
