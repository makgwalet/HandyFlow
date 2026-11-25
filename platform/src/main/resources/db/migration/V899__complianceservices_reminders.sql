-- Compliance Services: a daily digest of what is in the work queue, handed to the notification service for people an administrator chooses.
-- It is off until switched on. The log records that the digest was handed over, never that it was read.

CREATE TABLE IF NOT EXISTS compliance_reminder_settings (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL,
    enabled      BOOLEAN NOT NULL DEFAULT FALSE,
    min_severity VARCHAR(6) NOT NULL DEFAULT 'HIGH',
    max_items    INTEGER NOT NULL DEFAULT 25,
    updated_by   UUID,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version      BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_compliance_reminder_severity CHECK (min_severity IN ('HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT ck_compliance_reminder_max CHECK (max_items BETWEEN 5 AND 100)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_compliance_reminder_settings ON compliance_reminder_settings(tenant_id);

CREATE TABLE IF NOT EXISTS compliance_reminder_recipients (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    user_id     UUID NOT NULL,
    admin_items BOOLEAN NOT NULL DEFAULT FALSE,
    created_by  UUID,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version     BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_compliance_reminder_recipient ON compliance_reminder_recipients(tenant_id, user_id);

CREATE TABLE IF NOT EXISTS compliance_reminder_log (
    id         UUID PRIMARY KEY,
    tenant_id  UUID NOT NULL,
    user_id    UUID NOT NULL,
    run_date   DATE NOT NULL,
    item_count INTEGER NOT NULL DEFAULT 0,
    outcome    VARCHAR(12) NOT NULL,
    detail     VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version    BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_compliance_reminder_outcome CHECK (outcome IN ('HANDED_OVER', 'NOTHING_DUE', 'NO_ADDRESS', 'FAILED')),
    CONSTRAINT ck_compliance_reminder_count CHECK (item_count >= 0)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_compliance_reminder_log ON compliance_reminder_log(tenant_id, user_id, run_date);
CREATE INDEX IF NOT EXISTS idx_compliance_reminder_log_recent ON compliance_reminder_log(tenant_id, run_date DESC);
