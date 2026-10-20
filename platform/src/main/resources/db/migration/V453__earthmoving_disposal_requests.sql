-- Earthmoving: disposal requests. A machine leaves the fleet (sold, scrapped, written off, transferred) only through an approved,
-- evidenced request; completing it retires the machine. Evidence is kept in the Document Service (owner type EARTH_DISPOSAL).
CREATE TABLE earthmoving_disposal_requests (
    id               UUID PRIMARY KEY,
    tenant_id        UUID           NOT NULL,
    asset_id         UUID           NOT NULL REFERENCES earthmoving_assets (id),
    reason           VARCHAR(20)    NOT NULL,
    status           VARCHAR(20)    NOT NULL,
    details          TEXT           NOT NULL,
    counterparty     VARCHAR(255),
    proceeds         NUMERIC(15, 2),
    requested_by     UUID           NOT NULL,
    requested_at     TIMESTAMPTZ    NOT NULL,
    decided_by       UUID,
    decided_at       TIMESTAMPTZ,
    decision_note    TEXT,
    completed_by     UUID,
    completed_at     TIMESTAMPTZ,
    cancelled_by     UUID,
    cancelled_at     TIMESTAMPTZ,
    cancel_reason    TEXT,
    CONSTRAINT chk_em_disposal_reason CHECK (reason IN ('SOLD', 'SCRAPPED', 'WRITTEN_OFF', 'TRANSFERRED')),
    CONSTRAINT chk_em_disposal_status CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_em_disposal_proceeds CHECK (proceeds IS NULL OR proceeds >= 0),
    CONSTRAINT chk_em_disposal_approver CHECK (decided_by IS NULL OR decided_by <> requested_by)
);
-- One live request per machine.
CREATE UNIQUE INDEX uq_em_disposal_live ON earthmoving_disposal_requests (tenant_id, asset_id) WHERE status IN ('REQUESTED', 'APPROVED');
CREATE INDEX idx_em_disposal_tenant ON earthmoving_disposal_requests (tenant_id, requested_at DESC);

-- A new alert type for a request waiting to be approved.
ALTER TABLE earthmoving_alert_rules DROP CONSTRAINT IF EXISTS chk_em_alert_rule_type;
ALTER TABLE earthmoving_alert_rules ADD CONSTRAINT chk_em_alert_rule_type
    CHECK (alert_type IN ('SERVICE_DUE', 'BREAKDOWN', 'WORK_ORDER_OVERDUE', 'DOCUMENT_EXPIRY', 'DISPOSAL_APPROVAL'));
