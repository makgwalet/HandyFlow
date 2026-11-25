-- SMS reminders to clients. Off until the practice switches it on, and only ever to a number a person has recorded consent for.
-- Consent is withdrawn, never deleted. The log says "handed to the SMS provider", never "delivered".
CREATE TABLE IF NOT EXISTS compliance_sms_settings (
    id         UUID PRIMARY KEY,
    tenant_id  UUID NOT NULL,
    enabled    BOOLEAN NOT NULL DEFAULT FALSE,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version    BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_compliance_sms_settings ON compliance_sms_settings(tenant_id);

CREATE TABLE IF NOT EXISTS compliance_sms_consents (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    client_id      UUID NOT NULL,
    contact_name   VARCHAR(200) NOT NULL,
    phone          VARCHAR(20) NOT NULL,
    basis          VARCHAR(1000) NOT NULL,
    given_by       UUID NOT NULL,
    given_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    withdrawn_by   UUID,
    withdrawn_at   TIMESTAMPTZ,
    withdrawn_note VARCHAR(1000),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version        BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_compliance_sms_consent_phone CHECK (phone ~ '^\+[1-9][0-9]{7,14}$'),
    CONSTRAINT ck_compliance_sms_consent_basis CHECK (char_length(btrim(basis)) >= 10),
    CONSTRAINT ck_compliance_sms_consent_name CHECK (char_length(btrim(contact_name)) >= 1),
    CONSTRAINT ck_compliance_sms_consent_withdrawn CHECK (
        (withdrawn_at IS NULL AND withdrawn_by IS NULL AND withdrawn_note IS NULL)
        OR (withdrawn_at IS NOT NULL AND withdrawn_by IS NOT NULL AND char_length(btrim(withdrawn_note)) >= 10))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_compliance_sms_consent_active ON compliance_sms_consents(tenant_id, client_id, phone) WHERE withdrawn_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_compliance_sms_consent_client ON compliance_sms_consents(tenant_id, client_id);

CREATE TABLE IF NOT EXISTS compliance_sms_log (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    client_id   UUID NOT NULL,
    deadline_id UUID NOT NULL,
    consent_id  UUID NOT NULL,
    kind        VARCHAR(12) NOT NULL,
    run_date    DATE NOT NULL,
    outcome     VARCHAR(12) NOT NULL,
    detail      VARCHAR(500),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_compliance_sms_log_kind CHECK (kind IN ('WEEK_AHEAD', 'DAY_BEFORE')),
    CONSTRAINT ck_compliance_sms_log_outcome CHECK (outcome IN ('HANDED_OVER', 'FAILED'))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_compliance_sms_log ON compliance_sms_log(tenant_id, deadline_id, consent_id, kind) WHERE outcome = 'HANDED_OVER';
CREATE INDEX IF NOT EXISTS idx_compliance_sms_log_recent ON compliance_sms_log(tenant_id, run_date DESC);
