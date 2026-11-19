-- Maker-checker for contracting. Per-tenant policy (no row = the safe default in ContractApprovalService), approval requests bound to a hash of
-- exactly what was reviewed, and an append-only audit trail.
CREATE TABLE contract_approval_policies (
    id                        UUID PRIMARY KEY,
    tenant_id                 UUID        NOT NULL,
    action                    VARCHAR(30) NOT NULL,
    required                  BOOLEAN     NOT NULL,
    allow_sole_user_exception BOOLEAN     NOT NULL,
    updated_by                UUID,
    updated_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_contract_approval_policy UNIQUE (tenant_id, action),
    CONSTRAINT chk_contract_approval_policy_action CHECK (action IN ('SEND_FOR_SIGNING','PUBLISH_CLAUSE','PUBLISH_TEMPLATE','TERMINATE'))
);

CREATE TABLE contract_approvals (
    id                UUID PRIMARY KEY,
    tenant_id         UUID         NOT NULL,
    action            VARCHAR(30)  NOT NULL,
    subject_id        UUID         NOT NULL,
    subject_label     VARCHAR(300),
    subject_hash      VARCHAR(64)  NOT NULL,
    status            VARCHAR(12)  NOT NULL,
    requested_by      UUID         NOT NULL,
    requested_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    request_note      VARCHAR(1000),
    decided_by        UUID,
    decided_at        TIMESTAMP WITH TIME ZONE,
    decision_note     VARCHAR(1000),
    sole_user_exception BOOLEAN    NOT NULL DEFAULT FALSE,
    exception_reason  VARCHAR(1000),
    closed_at         TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_contract_approval_status CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED','INVALIDATED','CONSUMED')),
    CONSTRAINT chk_contract_approval_action CHECK (action IN ('SEND_FOR_SIGNING','PUBLISH_CLAUSE','PUBLISH_TEMPLATE','TERMINATE'))
);
-- at most one open (pending or approved-but-unused) request per subject and action
CREATE UNIQUE INDEX uq_contract_approval_open ON contract_approvals (tenant_id, action, subject_id) WHERE status IN ('PENDING','APPROVED');
CREATE INDEX idx_contract_approvals_tenant_status ON contract_approvals (tenant_id, status, requested_at DESC);

CREATE TABLE contract_approval_events (
    id           UUID PRIMARY KEY,
    tenant_id    UUID        NOT NULL,
    approval_id  UUID        NOT NULL REFERENCES contract_approvals (id),
    event_type   VARCHAR(30) NOT NULL,
    actor_user_id UUID,
    detail       VARCHAR(1000),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_contract_approval_events_approval ON contract_approval_events (approval_id, created_at);
