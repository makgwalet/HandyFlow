-- V___creative_multi_stakeholder_approval.sql
-- (rename to the next available Flyway version number in your sequence)
--
-- Backs multi-stakeholder sequential/parallel proof approval — "marketing
-- manager approves, then legal approves" or "both need to sign off, in
-- either order". Fully additive: existing single-approver proofs default
-- to approval_mode = 'SINGLE' and behave exactly as before. Rows only
-- appear in cre_proof_approvers for proofs a staff member has explicitly
-- configured for SEQUENTIAL or PARALLEL approval.

ALTER TABLE cre_proofs
    ADD COLUMN IF NOT EXISTS approval_mode VARCHAR(20) NOT NULL DEFAULT 'SINGLE';

CREATE TABLE IF NOT EXISTS cre_proof_approvers (
    id                UUID PRIMARY KEY,
    proof_id          UUID NOT NULL REFERENCES cre_proofs(id),
    tenant_id         UUID NOT NULL,
    approver_name     VARCHAR(255) NOT NULL,
    approver_email    VARCHAR(255) NOT NULL,
    approval_order    INT NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    approval_token    VARCHAR(128) NOT NULL UNIQUE,
    token_expires_at  TIMESTAMP NOT NULL,
    sent_at           TIMESTAMP,
    approved_at       TIMESTAMP,
    approved_by_ip    VARCHAR(64),
    rejection_reason  TEXT,
    created_at        TIMESTAMP NOT NULL DEFAULT now()
);

-- Backs resolveToken()'s public-facing lookup — the same access pattern as
-- cre_proofs.approval_token, just on the approver-level token instead.
CREATE INDEX IF NOT EXISTS idx_proof_approvers_token ON cre_proof_approvers(approval_token);
CREATE INDEX IF NOT EXISTS idx_proof_approvers_proof ON cre_proof_approvers(proof_id);
