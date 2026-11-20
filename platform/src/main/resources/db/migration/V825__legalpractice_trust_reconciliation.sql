-- V825: Legal Practice — trust account reconciliation. Confirm the next free Flyway version (V824 is LP chunk 4).
--
-- A reconciliation compares the trust bank statement at a date with the trust ledger at that date. Items that explain the
-- difference (deposits not yet on the statement, payments not yet cleared) are listed; what is left must be nil. One person
-- prepares and submits it; a different person approves it (unless the firm's self-approval setting says otherwise, in writing).
-- Once approved the period is locked: the ledger cannot take a transaction dated on or before the reconciled date.
-- Reconciliations are never deleted, and an approved one never changes.

CREATE TABLE IF NOT EXISTS lp_trust_reconciliations (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID          NOT NULL,
    period_end           DATE          NOT NULL,
    status               VARCHAR(12)   NOT NULL,
    statement_balance    NUMERIC(15,2) NOT NULL,
    statement_note       VARCHAR(500),
    -- Figures frozen when the reconciliation is submitted (null while it is a draft: the screen works them out live).
    cash_book_balance    NUMERIC(15,2),
    client_ledger_total  NUMERIC(15,2),
    outstanding_deposits NUMERIC(15,2),
    outstanding_payments NUMERIC(15,2),
    adjusted_bank_balance NUMERIC(15,2),
    difference           NUMERIC(15,2),
    ledger_mismatches    INTEGER,
    prepared_by          UUID          NOT NULL,
    prepared_by_name     VARCHAR(255),
    prepared_at          TIMESTAMPTZ   NOT NULL,
    submitted_by         UUID,
    submitted_at         TIMESTAMPTZ,
    return_reason        TEXT,
    returned_by_name     VARCHAR(255),
    approved_by          UUID,
    approved_by_name     VARCHAR(255),
    approved_at          TIMESTAMPTZ,
    approval_note        TEXT,
    self_approved        BOOLEAN       NOT NULL DEFAULT FALSE,
    version              BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_lp_tr_status    CHECK (status IN ('DRAFT','SUBMITTED','APPROVED')),
    CONSTRAINT ck_lp_tr_frozen    CHECK (status = 'DRAFT' OR (cash_book_balance IS NOT NULL AND client_ledger_total IS NOT NULL AND outstanding_deposits IS NOT NULL
                                         AND outstanding_payments IS NOT NULL AND adjusted_bank_balance IS NOT NULL AND difference IS NOT NULL AND submitted_by IS NOT NULL AND submitted_at IS NOT NULL)),
    -- Only a balanced reconciliation can be submitted, so an approved one always has a nil difference.
    CONSTRAINT ck_lp_tr_balanced  CHECK (status = 'DRAFT' OR difference = 0),
    CONSTRAINT ck_lp_tr_approved  CHECK (status <> 'APPROVED' OR (approved_by IS NOT NULL AND approved_at IS NOT NULL)),
    -- The approver is not the person who submitted it, unless the approval was recorded as a self-approval.
    CONSTRAINT ck_lp_tr_segregation CHECK (approved_by IS NULL OR approved_by <> submitted_by OR self_approved),
    -- Figures that disagree with the ledger need the approver to say why.
    CONSTRAINT ck_lp_tr_mismatch_note CHECK (status <> 'APPROVED' OR coalesce(ledger_mismatches, 0) = 0 OR length(btrim(coalesce(approval_note, ''))) >= 10)
);
CREATE INDEX IF NOT EXISTS idx_lp_tr_tenant ON lp_trust_reconciliations (tenant_id, period_end DESC);
-- A period is reconciled once, and only one reconciliation is open at a time.
CREATE UNIQUE INDEX IF NOT EXISTS uq_lp_tr_approved_period ON lp_trust_reconciliations (tenant_id, period_end) WHERE status = 'APPROVED';
CREATE UNIQUE INDEX IF NOT EXISTS uq_lp_tr_one_open ON lp_trust_reconciliations (tenant_id) WHERE status IN ('DRAFT','SUBMITTED');

CREATE TABLE IF NOT EXISTS lp_trust_recon_items (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID          NOT NULL,
    reconciliation_id    UUID          NOT NULL REFERENCES lp_trust_reconciliations(id),
    kind                 VARCHAR(20)   NOT NULL,
    amount               NUMERIC(15,2) NOT NULL,
    item_date            DATE          NOT NULL,
    description          VARCHAR(255)  NOT NULL,
    reference            VARCHAR(100),
    trust_transaction_id UUID,
    created_by           UUID,
    created_at           TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_lp_tri_kind   CHECK (kind IN ('OUTSTANDING_DEPOSIT','OUTSTANDING_PAYMENT')),
    CONSTRAINT ck_lp_tri_amount CHECK (amount > 0),
    CONSTRAINT ck_lp_tri_desc   CHECK (length(btrim(description)) >= 3)
);
CREATE INDEX IF NOT EXISTS idx_lp_tri_recon ON lp_trust_recon_items (tenant_id, reconciliation_id);
-- A ledger row explains at most one item per reconciliation.
CREATE UNIQUE INDEX IF NOT EXISTS uq_lp_tri_tx ON lp_trust_recon_items (reconciliation_id, trust_transaction_id) WHERE trust_transaction_id IS NOT NULL;

CREATE OR REPLACE FUNCTION lp_trust_recon_guard() RETURNS trigger AS $$
BEGIN
    IF TG_TABLE_NAME = 'lp_trust_reconciliations' THEN
        IF TG_OP = 'DELETE' THEN
            RAISE EXCEPTION 'Trust reconciliations are never deleted' USING ERRCODE = 'integrity_constraint_violation';
        END IF;
        IF OLD.status = 'APPROVED' THEN
            RAISE EXCEPTION 'An approved trust reconciliation cannot be changed' USING ERRCODE = 'integrity_constraint_violation';
        END IF;
        RETURN NEW;
    END IF;
    -- items: only while the reconciliation is a draft
    IF (SELECT status FROM lp_trust_reconciliations WHERE id = CASE WHEN TG_OP = 'DELETE' THEN OLD.reconciliation_id ELSE NEW.reconciliation_id END) <> 'DRAFT' THEN
        RAISE EXCEPTION 'Items can only be changed while the reconciliation is a draft' USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 'Reconciling items are added or removed, not edited' USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_lp_tr_guard ON lp_trust_reconciliations;
CREATE TRIGGER trg_lp_tr_guard BEFORE UPDATE OR DELETE ON lp_trust_reconciliations FOR EACH ROW EXECUTE FUNCTION lp_trust_recon_guard();
DROP TRIGGER IF EXISTS trg_lp_tri_guard ON lp_trust_recon_items;
CREATE TRIGGER trg_lp_tri_guard BEFORE INSERT OR UPDATE OR DELETE ON lp_trust_recon_items FOR EACH ROW EXECUTE FUNCTION lp_trust_recon_guard();

-- The lock: nothing may be posted to the trust ledger dated on or before a reconciled period end.
CREATE OR REPLACE FUNCTION lp_trust_period_lock() RETURNS trigger AS $$
DECLARE locked DATE;
BEGIN
    SELECT max(period_end) INTO locked FROM lp_trust_reconciliations WHERE tenant_id = NEW.tenant_id AND status = 'APPROVED';
    IF locked IS NOT NULL AND NEW.transaction_date <= locked THEN
        RAISE EXCEPTION 'The trust account is reconciled and locked up to %. A transaction dated % cannot be posted; date it after the locked period.', locked, NEW.transaction_date
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_lp_trust_period_lock ON lp_trust_transactions;
CREATE TRIGGER trg_lp_trust_period_lock BEFORE INSERT ON lp_trust_transactions FOR EACH ROW EXECUTE FUNCTION lp_trust_period_lock();

-- Permissions -------------------------------------------------------------------------------------------------------

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'LEGALPRACTICE_TRUST_RECONCILE',         'Prepare and submit a trust account reconciliation'),
    (gen_random_uuid(), 'LEGALPRACTICE_TRUST_RECONCILE_APPROVE', 'Approve a trust account reconciliation, which locks the period (a second person)')
ON CONFLICT (name) DO NOTHING;

UPDATE permissions SET module_key = 'legalpractice', resource = 'trust-reconciliation',
       verb = CASE name WHEN 'LEGALPRACTICE_TRUST_RECONCILE' THEN 'RECONCILE' ELSE 'APPROVE' END,
       sensitivity = 'FINANCIAL',
       requires_second_person = (name = 'LEGALPRACTICE_TRUST_RECONCILE_APPROVE'),
       grantable_by = 'ADMIN'
 WHERE name IN ('LEGALPRACTICE_TRUST_RECONCILE', 'LEGALPRACTICE_TRUST_RECONCILE_APPROVE');

-- Whoever could pay out of trust (ADMIN) can prepare and approve; segregation is enforced per person, not per role.
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.id
  FROM role_permissions rp
  JOIN permissions src ON src.id = rp.permission_id AND src.name = 'LEGALPRACTICE_ADMIN'
  JOIN permissions np ON np.name IN ('LEGALPRACTICE_TRUST_RECONCILE', 'LEGALPRACTICE_TRUST_RECONCILE_APPROVE')
 WHERE NOT EXISTS (SELECT 1 FROM role_permissions x WHERE x.role_id = rp.role_id AND x.permission_id = np.id);
