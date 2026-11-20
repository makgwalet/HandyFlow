-- V822: Legal Practice — payments out of trust need two people. Confirm the next free Flyway version before applying
-- (V819/V820 are Legal Compliance, V821 is the previous Legal Practice chunk).
--
-- 1. lp_trust_payment_requests: one person asks, another approves, and approval posts the ledger row. Never deleted.
-- 2. lp_trust_setting_changes: the firm's sole-practitioner exception (one person may approve their own request, with a
--    written reason every time). Append-only: the setting in force is the latest row; no rows means strict.
-- 3. The ledger records which request and which approver released each payment.
-- 4. Three granular permissions. Roles that held LEGALPRACTICE_ADMIN (the only role that could pay out of trust until now)
--    keep that ability; roles that held LEGALPRACTICE_MANAGE keep recording receipts. Nobody gains or loses anything.

CREATE TABLE IF NOT EXISTS lp_trust_payment_requests (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID          NOT NULL,
    client_id             UUID          NOT NULL,
    matter_id             UUID,
    payment_type          VARCHAR(30)   NOT NULL,
    amount                NUMERIC(15,2) NOT NULL,
    transaction_date      DATE,
    invoice_id            UUID,
    payee                 VARCHAR(255),
    reference             VARCHAR(255),
    notes                 TEXT,
    status                VARCHAR(12)   NOT NULL DEFAULT 'PENDING',
    requested_by          UUID          NOT NULL,
    requested_by_name     VARCHAR(255),
    requested_at          TIMESTAMPTZ   NOT NULL,
    decided_by            UUID,
    decided_by_name       VARCHAR(255),
    decided_at            TIMESTAMPTZ,
    decision_reason       TEXT,
    self_approved         BOOLEAN       NOT NULL DEFAULT FALSE,
    result_transaction_id UUID,
    idempotency_key       VARCHAR(80),
    version               BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_lp_tpr_type   CHECK (payment_type IN ('TRANSFER_TO_BUSINESS','DISBURSEMENT_PAYMENT','REFUND')),
    CONSTRAINT ck_lp_tpr_status CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED')),
    CONSTRAINT ck_lp_tpr_amount CHECK (amount > 0),
    -- The same person may only both ask and approve when it was recorded as a self-approval with a written reason.
    CONSTRAINT ck_lp_tpr_segregation CHECK (status <> 'APPROVED' OR decided_by IS DISTINCT FROM requested_by
                                            OR (self_approved AND length(btrim(coalesce(decision_reason, ''))) >= 10)),
    CONSTRAINT ck_lp_tpr_approved_has_tx CHECK (status <> 'APPROVED' OR result_transaction_id IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_lp_tpr_tenant_status ON lp_trust_payment_requests (tenant_id, status, requested_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uq_lp_tpr_idempotency ON lp_trust_payment_requests (tenant_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_lp_tpr_client        ON lp_trust_payment_requests (tenant_id, client_id);

CREATE OR REPLACE FUNCTION lp_trust_payment_requests_no_delete() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'lp_trust_payment_requests rows are never deleted: withdraw or reject the request instead'
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_lp_trust_payment_requests_no_delete ON lp_trust_payment_requests;
CREATE TRIGGER trg_lp_trust_payment_requests_no_delete
    BEFORE DELETE ON lp_trust_payment_requests
    FOR EACH ROW EXECUTE FUNCTION lp_trust_payment_requests_no_delete();

CREATE TABLE IF NOT EXISTS lp_trust_setting_changes (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID        NOT NULL,
    self_approval_allowed BOOLEAN     NOT NULL,
    reason                TEXT        NOT NULL,
    changed_by            UUID        NOT NULL,
    changed_by_name       VARCHAR(255),
    changed_at            TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_lp_tsc_reason CHECK (length(btrim(reason)) >= 10)
);

CREATE INDEX IF NOT EXISTS idx_lp_tsc_tenant ON lp_trust_setting_changes (tenant_id, changed_at DESC);

CREATE OR REPLACE FUNCTION lp_trust_setting_changes_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'lp_trust_setting_changes is append-only: % is not allowed', TG_OP
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_lp_trust_setting_changes_append_only ON lp_trust_setting_changes;
CREATE TRIGGER trg_lp_trust_setting_changes_append_only
    BEFORE UPDATE OR DELETE ON lp_trust_setting_changes
    FOR EACH ROW EXECUTE FUNCTION lp_trust_setting_changes_append_only();

-- The ledger row records which request released it and who approved. Inserted with the row, never updated afterwards
-- (the ledger is append-only since V821).
ALTER TABLE lp_trust_transactions
    ADD COLUMN IF NOT EXISTS request_id        UUID,
    ADD COLUMN IF NOT EXISTS approved_by       UUID,
    ADD COLUMN IF NOT EXISTS approved_by_name  VARCHAR(255);

-- One payment per request, enforced where it cannot be bypassed.
CREATE UNIQUE INDEX IF NOT EXISTS uq_lp_trust_transactions_request
    ON lp_trust_transactions (request_id) WHERE request_id IS NOT NULL;

-- Permissions -----------------------------------------------------------------------------------------------------
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'LEGALPRACTICE_TRUST_RECEIVE',         'Record money received into a client''s trust account'),
    (gen_random_uuid(), 'LEGALPRACTICE_TRUST_PAYMENT_REQUEST', 'Request a payment out of a client''s trust account'),
    (gen_random_uuid(), 'LEGALPRACTICE_TRUST_PAYMENT_APPROVE', 'Approve or reject a payment out of trust (a second person)')
ON CONFLICT (name) DO NOTHING;

UPDATE permissions SET module_key = 'legalpractice', resource = 'trust',
       verb = CASE name WHEN 'LEGALPRACTICE_TRUST_RECEIVE' THEN 'RECEIVE'
                        WHEN 'LEGALPRACTICE_TRUST_PAYMENT_REQUEST' THEN 'PAYMENT_REQUEST'
                        ELSE 'PAYMENT_APPROVE' END,
       sensitivity = 'FINANCIAL',
       requires_second_person = (name = 'LEGALPRACTICE_TRUST_PAYMENT_APPROVE'),
       grantable_by = 'ADMIN'
 WHERE name IN ('LEGALPRACTICE_TRUST_RECEIVE', 'LEGALPRACTICE_TRUST_PAYMENT_REQUEST', 'LEGALPRACTICE_TRUST_PAYMENT_APPROVE');

-- Whoever could pay out of trust (ADMIN) can request and approve; whoever could record a receipt (MANAGE) still can.
INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, np.id
  FROM role_permissions rp
  JOIN permissions admin_p ON admin_p.id = rp.permission_id AND admin_p.name = 'LEGALPRACTICE_ADMIN'
  JOIN permissions np ON np.name IN ('LEGALPRACTICE_TRUST_RECEIVE', 'LEGALPRACTICE_TRUST_PAYMENT_REQUEST', 'LEGALPRACTICE_TRUST_PAYMENT_APPROVE')
 WHERE NOT EXISTS (SELECT 1 FROM role_permissions x WHERE x.role_id = rp.role_id AND x.permission_id = np.id);

INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, np.id
  FROM role_permissions rp
  JOIN permissions mp ON mp.id = rp.permission_id AND mp.name = 'LEGALPRACTICE_MANAGE'
  JOIN permissions np ON np.name = 'LEGALPRACTICE_TRUST_RECEIVE'
 WHERE NOT EXISTS (SELECT 1 FROM role_permissions x WHERE x.role_id = rp.role_id AND x.permission_id = np.id);
