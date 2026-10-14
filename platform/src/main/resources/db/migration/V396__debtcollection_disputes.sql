-- Finance F16: dispute records for Debt Collection. A dispute has an owner, a response due date,
-- an optional invoice, and a resolution. Opening one pauses the case (status DISPUTED, already
-- honoured by messages, fees, priority and legal handover); resolving the last open one restores it.

CREATE TABLE IF NOT EXISTS debtcollection_disputes (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL,
    case_id            UUID NOT NULL REFERENCES debtcollection_cases (id),
    invoice_id         UUID,
    reason_type        VARCHAR(25) NOT NULL,
    description        TEXT NOT NULL,
    amount_disputed    NUMERIC(15,2),
    status             VARCHAR(15) NOT NULL DEFAULT 'OPEN',
    raised_on          DATE NOT NULL,
    response_due       DATE NOT NULL,
    owner_user_id      UUID,
    owner_user_name    VARCHAR(255),
    resolution         VARCHAR(15),
    amount_conceded    NUMERIC(15,2),
    resolution_note    TEXT,
    resolved_by        UUID,
    resolved_by_name   VARCHAR(255),
    resolved_at        TIMESTAMPTZ,
    credit_note_ref    VARCHAR(60),
    case_status_before VARCHAR(25),
    created_by         UUID,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT dc_disputes_status_chk CHECK (status IN ('OPEN','UNDER_REVIEW','RESOLVED')),
    CONSTRAINT dc_disputes_resolution_chk CHECK (resolution IS NULL OR resolution IN ('UPHELD','PARTLY_UPHELD','REJECTED','WITHDRAWN')),
    CONSTRAINT dc_disputes_resolved_chk CHECK ((status = 'RESOLVED') = (resolution IS NOT NULL))
);

CREATE INDEX IF NOT EXISTS idx_dc_disputes_case ON debtcollection_disputes (tenant_id, case_id);
CREATE INDEX IF NOT EXISTS idx_dc_disputes_open ON debtcollection_disputes (tenant_id, response_due) WHERE status <> 'RESOLVED';
