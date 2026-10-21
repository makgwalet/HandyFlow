-- V470: scheduled CRM reports. A schedule says which report, how often, and who gets it (no recipients = the account's admins).
CREATE TABLE crm_report_schedules (
    id            UUID PRIMARY KEY,
    tenant_id     UUID         NOT NULL,
    name          VARCHAR(120) NOT NULL,
    report_type   VARCHAR(20)  NOT NULL,
    frequency     VARCHAR(10)  NOT NULL,
    recipient_ids TEXT         NOT NULL DEFAULT '',     -- comma-separated user ids; empty = tenant admins
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    last_sent_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ
);
CREATE INDEX idx_crm_report_schedules_tenant ON crm_report_schedules (tenant_id) WHERE deleted_at IS NULL;
