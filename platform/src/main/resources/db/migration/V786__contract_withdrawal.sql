-- Withdrawing a contract that was sent for signing: the signing attempt is cancelled, the record and its evidence stay.
-- contracts.status is a plain string, so WITHDRAWN needs no constraint change. What is new:
--   1. WITHDRAW becomes an action that can be put behind maker-checker (on by default).
--   2. contract_withdrawals: one immutable row per withdrawal (who, when, why, what was revoked).
--   3. A new draft revision points back at the withdrawn contract it was made from.
ALTER TABLE contract_approval_policies DROP CONSTRAINT chk_contract_approval_policy_action;
ALTER TABLE contract_approval_policies ADD CONSTRAINT chk_contract_approval_policy_action
    CHECK (action IN ('SEND_FOR_SIGNING','PUBLISH_CLAUSE','PUBLISH_TEMPLATE','TERMINATE','ACCEPT_COUNTERPARTY_REDLINE','SIGN_ON_BEHALF','WITHDRAW'));

ALTER TABLE contract_approvals DROP CONSTRAINT chk_contract_approval_action;
ALTER TABLE contract_approvals ADD CONSTRAINT chk_contract_approval_action
    CHECK (action IN ('SEND_FOR_SIGNING','PUBLISH_CLAUSE','PUBLISH_TEMPLATE','TERMINATE','ACCEPT_COUNTERPARTY_REDLINE','SIGN_ON_BEHALF','WITHDRAW'));

CREATE TABLE contract_withdrawals (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID        NOT NULL,
    contract_id          UUID        NOT NULL REFERENCES contracts(id),
    withdrawn_by         UUID,
    withdrawn_at         TIMESTAMPTZ NOT NULL,
    reason               TEXT        NOT NULL CHECK (length(btrim(reason)) >= 10),
    body_hash            VARCHAR(64),
    links_revoked        INT         NOT NULL DEFAULT 0,
    otp_challenges_cleared INT       NOT NULL DEFAULT 0,
    parties_signed       INT         NOT NULL DEFAULT 0,
    parties_total        INT         NOT NULL DEFAULT 0,
    approval_id          UUID
);
CREATE UNIQUE INDEX uq_contract_withdrawals_contract ON contract_withdrawals (contract_id);
CREATE INDEX idx_contract_withdrawals_tenant ON contract_withdrawals (tenant_id, withdrawn_at DESC);

CREATE OR REPLACE FUNCTION contract_withdrawals_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'A withdrawal record cannot be changed or deleted';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_contract_withdrawals_immutable
    BEFORE UPDATE OR DELETE ON contract_withdrawals
    FOR EACH ROW EXECUTE FUNCTION contract_withdrawals_immutable();

ALTER TABLE contracts ADD COLUMN revision_of_contract_id UUID REFERENCES contracts(id);
ALTER TABLE contracts ADD COLUMN revision_no INT NOT NULL DEFAULT 1;
-- A withdrawn contract can be revised once: the one new draft that carries its text forward.
CREATE UNIQUE INDEX uq_contracts_one_revision_per_withdrawn ON contracts (revision_of_contract_id) WHERE revision_of_contract_id IS NOT NULL;
