-- V482__catalogue_item_approval.sql
-- Catalogue slice 7: approval before a draft item goes live.
-- A tenant that switches it on (catalogue_settings.item_approval_required) has new items saved as drafts unless the
-- creator may approve; the creator submits the draft, a different person with CATALOGUE_APPROVE approves it (the item
-- becomes active) or rejects it with a reason. Existing tenants keep today's behaviour: the setting defaults to off.

ALTER TABLE catalogue_settings ADD COLUMN IF NOT EXISTS item_approval_required BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS catalogue_item_approvals (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id      UUID         NOT NULL,
    item_id        UUID         NOT NULL REFERENCES catalogue_items(id) ON DELETE CASCADE,
    status         VARCHAR(10)  NOT NULL,
    requested_by   UUID,
    requested_at   TIMESTAMP    NOT NULL DEFAULT now(),
    note           VARCHAR(255),
    decided_by     UUID,
    decided_at     TIMESTAMP,
    decision_note  VARCHAR(255),
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_catalogue_item_approvals PRIMARY KEY (id),
    CONSTRAINT ck_catalogue_item_approvals_status CHECK (status IN ('PENDING','APPROVED','REJECTED','WITHDRAWN')),
    -- whoever asked can never be the one who approves or rejects (a requester may withdraw their own request)
    CONSTRAINT ck_catalogue_item_approvals_not_self CHECK (status IN ('PENDING','WITHDRAWN') OR decided_by IS NULL OR requested_by IS NULL OR decided_by <> requested_by)
);
-- one open request per item
CREATE UNIQUE INDEX IF NOT EXISTS uq_catalogue_item_approvals_open ON catalogue_item_approvals (item_id) WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS ix_catalogue_item_approvals_queue ON catalogue_item_approvals (tenant_id, status, requested_at);
