-- Delegated authority: who may approve, negotiate, sign, renew or terminate contracts for the organisation, up to what value.
-- Enforcement is off until an administrator switches it on, so existing behaviour does not change by itself.
CREATE TABLE contract_authority_settings (
    tenant_id   UUID PRIMARY KEY,
    enforced    BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_by  UUID,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE contract_authority_grants (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID        NOT NULL,
    grantee_user_id    UUID,
    grantee_role       VARCHAR(64),
    business_unit      VARCHAR(120),
    contract_types     VARCHAR(600) NOT NULL DEFAULT '',
    actions            VARCHAR(200) NOT NULL,
    unlimited_amount   BOOLEAN     NOT NULL DEFAULT FALSE,
    max_amount         NUMERIC(18,2),
    effective_from     DATE        NOT NULL,
    expires_on         DATE,
    conditions         TEXT,
    delegated_from_id  UUID REFERENCES contract_authority_grants(id),
    delegated_by       UUID,
    status             VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    revoked_at         TIMESTAMPTZ,
    revoked_by         UUID,
    revoked_reason     TEXT,
    created_by         UUID,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_authority_one_grantee CHECK ((grantee_user_id IS NOT NULL) <> (grantee_role IS NOT NULL)),
    CONSTRAINT chk_authority_amount CHECK (unlimited_amount OR max_amount >= 0),
    CONSTRAINT chk_authority_window CHECK (expires_on IS NULL OR expires_on >= effective_from),
    CONSTRAINT chk_authority_status CHECK (status IN ('ACTIVE','REVOKED')),
    CONSTRAINT chk_authority_delegation CHECK (delegated_from_id IS NULL OR (expires_on IS NOT NULL AND grantee_user_id IS NOT NULL))
);
CREATE INDEX idx_authority_grants_tenant ON contract_authority_grants (tenant_id, status);
CREATE INDEX idx_authority_grants_user ON contract_authority_grants (tenant_id, grantee_user_id);

CREATE TABLE contract_authority_events (
    id             UUID PRIMARY KEY,
    tenant_id      UUID        NOT NULL,
    grant_id       UUID,
    event_type     VARCHAR(40) NOT NULL,
    actor_user_id  UUID,
    detail         TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_authority_events_tenant ON contract_authority_events (tenant_id, created_at DESC);

CREATE OR REPLACE FUNCTION contract_authority_events_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'An authority audit record cannot be changed or deleted';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_contract_authority_events_immutable
    BEFORE UPDATE OR DELETE ON contract_authority_events
    FOR EACH ROW EXECUTE FUNCTION contract_authority_events_immutable();
