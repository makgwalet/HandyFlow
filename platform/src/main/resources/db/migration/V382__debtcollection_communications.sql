-- Debt collection communications: the three tables the Communication, CommunicationTemplate and CommunicationOptOut entities map to.
-- (Added 2026-10-13: the entities were committed without a migration, so Hibernate schema validation stopped the application at start-up.)
-- Base columns follow the other debt collection tables (id, tenant_id, created_at, updated_at, version).

-- Do-not-contact list. A revoked entry is kept: who opted out and who lifted it must survive.
CREATE TABLE IF NOT EXISTS debtcollection_communication_opt_outs (
    id                     UUID PRIMARY KEY,
    tenant_id              UUID          NOT NULL,
    created_at             TIMESTAMPTZ   NOT NULL,
    updated_at             TIMESTAMPTZ   NOT NULL,
    version                BIGINT        NOT NULL DEFAULT 0,
    contact_value          VARCHAR(255)  NOT NULL,              -- normalised address or number
    channel                VARCHAR(15),                         -- NULL = every channel
    reason                 VARCHAR(500)  NOT NULL,
    source                 VARCHAR(30)   NOT NULL,              -- INBOUND_KEYWORD, STAFF or DEBTOR_REQUEST
    recorded_by_user_id    UUID,
    recorded_by_user_name  VARCHAR(255),
    revoked_at             TIMESTAMPTZ,
    revoked_by             UUID,
    revoke_reason          VARCHAR(500)
);
CREATE INDEX IF NOT EXISTS ix_dc_optouts_contact ON debtcollection_communication_opt_outs (tenant_id, contact_value) WHERE revoked_at IS NULL;
-- At most one active entry per contact and channel (all-channel entries are one more "channel").
CREATE UNIQUE INDEX IF NOT EXISTS uq_dc_optouts_active ON debtcollection_communication_opt_outs (tenant_id, contact_value, COALESCE(channel, '*')) WHERE revoked_at IS NULL;

-- Message templates, versioned; exactly one latest version per (tenant, code).
CREATE TABLE IF NOT EXISTS debtcollection_communication_templates (
    id                UUID PRIMARY KEY,
    tenant_id         UUID          NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    version           BIGINT        NOT NULL DEFAULT 0,
    code              VARCHAR(60)   NOT NULL,
    template_version  INT           NOT NULL,
    channel           VARCHAR(15)   NOT NULL,
    subject           VARCHAR(255),
    body              TEXT          NOT NULL,
    is_latest         BOOLEAN       NOT NULL,
    created_by        UUID
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_dc_templates_version ON debtcollection_communication_templates (tenant_id, code, template_version);
CREATE UNIQUE INDEX IF NOT EXISTS uq_dc_templates_latest  ON debtcollection_communication_templates (tenant_id, code) WHERE is_latest;

-- Every message sent, received or posted for a case.
CREATE TABLE IF NOT EXISTS debtcollection_communications (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID          NOT NULL,
    created_at            TIMESTAMPTZ   NOT NULL,
    updated_at            TIMESTAMPTZ   NOT NULL,
    version               BIGINT        NOT NULL DEFAULT 0,
    case_id               UUID          NOT NULL REFERENCES debtcollection_cases (id),
    channel               VARCHAR(15)   NOT NULL,
    direction             VARCHAR(10)   NOT NULL,
    purpose               VARCHAR(30)   NOT NULL,
    status                VARCHAR(15)   NOT NULL,
    recipient             VARCHAR(500)  NOT NULL,
    subject               VARCHAR(255),
    body                  TEXT,
    template_code         VARCHAR(60),
    template_version      INT,
    provider_message_id   VARCHAR(100),
    status_detail         VARCHAR(500),
    processing_basis      VARCHAR(100),
    dispatch_method       VARCHAR(20),
    tracking_reference    VARCHAR(100),
    evidence_id           UUID,
    idempotency_key       VARCHAR(100),
    sent_at               TIMESTAMPTZ,
    delivered_at          TIMESTAMPTZ,
    received_at           TIMESTAMPTZ,
    created_by_user_id    UUID,
    created_by_user_name  VARCHAR(255)
);
CREATE INDEX IF NOT EXISTS ix_dc_comms_case ON debtcollection_communications (tenant_id, case_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_dc_comms_outbound_provider ON debtcollection_communications (provider_message_id) WHERE direction = 'OUTBOUND' AND provider_message_id IS NOT NULL;
-- The database side of the application's de-duplication: the same request, or the same inbound provider message, is recorded once.
CREATE UNIQUE INDEX IF NOT EXISTS uq_dc_comms_idempotency ON debtcollection_communications (tenant_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_dc_comms_inbound_provider ON debtcollection_communications (tenant_id, provider_message_id) WHERE direction = 'INBOUND' AND provider_message_id IS NOT NULL;
