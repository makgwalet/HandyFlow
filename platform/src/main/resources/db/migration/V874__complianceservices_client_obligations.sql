-- Compliance Services: which catalogue obligations apply to which client. A suggestion comes from the
-- obligation's conditions and the client's profile; a person can confirm or override it with a recorded
-- reason. Hiding an obligation the rules say applies needs a second person's approval.

CREATE TABLE IF NOT EXISTS compliance_client_obligations (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    client_id             UUID NOT NULL REFERENCES compliance_service_clients(id) ON DELETE CASCADE,
    obligation_id         UUID NOT NULL REFERENCES compliance_obligations(id) ON DELETE CASCADE,
    version_id            UUID NOT NULL REFERENCES compliance_obligation_versions(id),
    version_number        INTEGER NOT NULL,
    state                 VARCHAR(15) NOT NULL,            -- APPLICABLE, NOT_APPLICABLE, PENDING
    suggested_state       VARCHAR(15) NOT NULL,
    suggestion_reasons    TEXT,
    based_on_unconfirmed  BOOLEAN NOT NULL DEFAULT FALSE,
    decided_by            UUID,                            -- null while the state is only the system's suggestion
    decided_at            TIMESTAMPTZ,
    decided_version_number INTEGER,
    decision_reason       TEXT,
    evidence_ref          VARCHAR(300),
    effective_from        DATE,
    review_due            DATE,
    approval_status       VARCHAR(12) NOT NULL DEFAULT 'NOT_REQUIRED',   -- NOT_REQUIRED, AWAITING, APPROVED
    approved_by           UUID,
    approved_at           TIMESTAMPTZ,
    approval_exception_reason TEXT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_compliance_client_obligation UNIQUE (client_id, obligation_id),
    CONSTRAINT ck_client_obligation_review CHECK (state <> 'NOT_APPLICABLE' OR decided_by IS NULL OR review_due IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS idx_compliance_client_obligations ON compliance_client_obligations(tenant_id, client_id);

CREATE TABLE IF NOT EXISTS compliance_client_obligation_history (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    assignment_id   UUID NOT NULL REFERENCES compliance_client_obligations(id) ON DELETE CASCADE,
    from_state      VARCHAR(15),
    to_state        VARCHAR(15) NOT NULL,
    event           VARCHAR(20) NOT NULL,                  -- SUGGESTED, RESUGGESTED, DECIDED, APPROVED
    reason          TEXT,
    actor           UUID,
    version_number  INTEGER,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_compliance_client_obligation_history ON compliance_client_obligation_history(tenant_id, assignment_id);
