-- Supply Chain: supplier lifecycle (approval, suspension), controlled bank-detail changes, supplier audit trail.

ALTER TABLE sc_suppliers ADD COLUMN IF NOT EXISTS status_reason     VARCHAR(500);
ALTER TABLE sc_suppliers ADD COLUMN IF NOT EXISTS status_changed_at TIMESTAMPTZ;
ALTER TABLE sc_suppliers ADD COLUMN IF NOT EXISTS status_changed_by UUID;

-- V86 allowed only ACTIVE / INACTIVE / BLACKLISTED. Add PENDING_APPROVAL and SUSPENDED.
ALTER TABLE sc_suppliers DROP CONSTRAINT IF EXISTS sc_suppliers_status_check;
ALTER TABLE sc_suppliers DROP CONSTRAINT IF EXISTS chk_sc_supplier_status;
ALTER TABLE sc_suppliers
    ADD CONSTRAINT chk_sc_supplier_status CHECK (status IN ('PENDING_APPROVAL','ACTIVE','SUSPENDED','BLACKLISTED','INACTIVE'));

-- Fraud signal lookup ("same account on another supplier").
CREATE INDEX IF NOT EXISTS idx_sc_suppliers_bank_account ON sc_suppliers(tenant_id, bank_account)
    WHERE bank_account IS NOT NULL AND deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS sc_supplier_bank_changes (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID          NOT NULL,
    supplier_id             UUID          NOT NULL REFERENCES sc_suppliers(id),
    status                  VARCHAR(24)   NOT NULL,
    proposed_bank_name      VARCHAR(255)  NOT NULL,
    proposed_account_enc    TEXT          NOT NULL,
    proposed_account_masked VARCHAR(40)   NOT NULL,
    proposed_branch_code    VARCHAR(20),
    previous_bank_name      VARCHAR(255),
    previous_account_masked VARCHAR(40),
    reason                  VARCHAR(500)  NOT NULL,
    warnings                VARCHAR(1000),
    requested_by            UUID,
    requested_by_name       VARCHAR(255),
    verified_by             UUID,
    verified_by_name        VARCHAR(255),
    verified_at             TIMESTAMPTZ,
    verification_method     VARCHAR(30),
    verification_note       VARCHAR(500),
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_sc_bank_change_status CHECK (status IN ('PENDING_VERIFICATION','APPLIED','REJECTED','CANCELLED'))
);
CREATE INDEX IF NOT EXISTS idx_sc_bank_changes_supplier ON sc_supplier_bank_changes(tenant_id, supplier_id, created_at DESC);
-- One open change per supplier, race-safe (the service also checks, but two requests can pass that check together).
CREATE UNIQUE INDEX IF NOT EXISTS uq_sc_bank_change_open ON sc_supplier_bank_changes(supplier_id) WHERE status = 'PENDING_VERIFICATION';

CREATE TABLE IF NOT EXISTS sc_supplier_events (
    id          UUID PRIMARY KEY,
    tenant_id   UUID          NOT NULL,
    supplier_id UUID          NOT NULL REFERENCES sc_suppliers(id),
    event_type  VARCHAR(40)   NOT NULL,
    from_status VARCHAR(20),
    to_status   VARCHAR(20),
    detail      VARCHAR(1000),
    actor       UUID,
    actor_name  VARCHAR(255),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_sc_supplier_events ON sc_supplier_events(tenant_id, supplier_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_sc_supplier_events_type ON sc_supplier_events(supplier_id, event_type, created_at DESC);
