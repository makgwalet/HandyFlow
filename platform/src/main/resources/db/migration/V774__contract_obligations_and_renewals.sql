-- Obligations: things a contract requires someone to do by a date (a payment, a delivery, a report, a notice).
-- Entered by staff. Nothing here is extracted or interpreted automatically.
CREATE TABLE contract_obligations (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    contract_id       UUID NOT NULL REFERENCES contracts(id),
    title             VARCHAR(200) NOT NULL,
    description       TEXT,
    obligation_type   VARCHAR(30)  NOT NULL,
    responsible_side  VARCHAR(20)  NOT NULL,           -- OURS or COUNTERPARTY
    owner_email       VARCHAR(255),                    -- who is reminded; optional
    due_date          DATE NOT NULL,
    recurrence        VARCHAR(20)  NOT NULL DEFAULT 'NONE',
    status            VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    completed_at      TIMESTAMP,
    completed_by      UUID,
    completion_note   TEXT,
    waived_reason     TEXT,
    last_reminded_on  DATE,
    created_by        UUID,
    created_at        TIMESTAMP NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_obligation_status CHECK (status IN ('OPEN', 'IN_PROGRESS', 'COMPLETED', 'WAIVED')),
    CONSTRAINT chk_obligation_side CHECK (responsible_side IN ('OURS', 'COUNTERPARTY')),
    CONSTRAINT chk_obligation_recurrence CHECK (recurrence IN ('NONE', 'MONTHLY', 'QUARTERLY', 'ANNUALLY'))
);
CREATE INDEX idx_contract_obligations_contract ON contract_obligations (tenant_id, contract_id);
CREATE INDEX idx_contract_obligations_due ON contract_obligations (tenant_id, status, due_date);

-- Renewal decisions: what the business decided as a signed contract approaches its end date. A RENEW decision creates a
-- successor draft that has to be reviewed and signed like any other contract; nothing is extended automatically.
CREATE TABLE contract_renewal_decisions (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    contract_id             UUID NOT NULL REFERENCES contracts(id),
    decision                VARCHAR(20) NOT NULL,
    note                    TEXT,
    decided_by              UUID,
    decided_at              TIMESTAMP NOT NULL DEFAULT now(),
    successor_contract_id   UUID REFERENCES contracts(id),
    CONSTRAINT chk_renewal_decision CHECK (decision IN ('RENEW', 'RENEGOTIATE', 'DO_NOT_RENEW'))
);
CREATE INDEX idx_contract_renewal_decisions_contract ON contract_renewal_decisions (tenant_id, contract_id, decided_at DESC);

ALTER TABLE contracts ADD COLUMN IF NOT EXISTS renewed_from_contract_id UUID REFERENCES contracts(id);
