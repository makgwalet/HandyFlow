-- E4: alert rules (who is told, and who is told next if nobody acknowledges), alerts with acknowledgement and
-- resolution, and a record of who was notified and whether it was delivered.
CREATE TABLE IF NOT EXISTS earthmoving_alert_rules (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    alert_type              VARCHAR(30) NOT NULL,
    enabled                 BOOLEAN NOT NULL DEFAULT TRUE,
    escalate_after_minutes  INTEGER CHECK (escalate_after_minutes IS NULL OR escalate_after_minutes >= 0),
    created_at              TIMESTAMP NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_em_alert_rule UNIQUE (tenant_id, alert_type),
    CONSTRAINT chk_em_alert_rule_type CHECK (alert_type IN ('SERVICE_DUE', 'BREAKDOWN', 'WORK_ORDER_OVERDUE'))
);

CREATE TABLE IF NOT EXISTS earthmoving_alert_recipients (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  UUID NOT NULL,
    rule_id    UUID NOT NULL REFERENCES earthmoving_alert_rules(id) ON DELETE CASCADE,
    user_id    UUID NOT NULL,
    level      SMALLINT NOT NULL CHECK (level IN (1, 2)),
    CONSTRAINT uq_em_alert_recipient UNIQUE (rule_id, user_id, level)
);

CREATE TABLE IF NOT EXISTS earthmoving_alerts (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL,
    alert_type        VARCHAR(30) NOT NULL,
    severity          VARCHAR(10) NOT NULL DEFAULT 'WARNING',
    asset_id          UUID REFERENCES earthmoving_assets(id),
    ref_type          VARCHAR(30),
    ref_id            UUID,
    title             VARCHAR(255) NOT NULL,
    message           TEXT NOT NULL,
    dedupe_key        VARCHAR(120) NOT NULL,
    status            VARCHAR(15) NOT NULL DEFAULT 'OPEN',
    level             SMALLINT NOT NULL DEFAULT 1,
    created_at        TIMESTAMP NOT NULL DEFAULT now(),
    escalated_at      TIMESTAMP,
    acknowledged_by   UUID,
    acknowledged_at   TIMESTAMP,
    resolved_by       UUID,
    resolved_at       TIMESTAMP,
    resolution_note   TEXT,
    version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_em_alert_status CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED')),
    CONSTRAINT chk_em_alert_severity CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL'))
);
-- One live alert per cause: a second report of the same thing does not send a second round of messages.
CREATE UNIQUE INDEX IF NOT EXISTS uq_em_alert_live_dedupe ON earthmoving_alerts (tenant_id, dedupe_key) WHERE status <> 'RESOLVED';
CREATE INDEX IF NOT EXISTS idx_em_alerts_tenant_status ON earthmoving_alerts (tenant_id, status, created_at DESC);

CREATE TABLE IF NOT EXISTS earthmoving_alert_deliveries (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  UUID NOT NULL,
    alert_id   UUID NOT NULL REFERENCES earthmoving_alerts(id) ON DELETE CASCADE,
    user_id    UUID,
    level      SMALLINT NOT NULL,
    status     VARCHAR(10) NOT NULL,
    detail     VARCHAR(255),
    sent_at    TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_em_alert_delivery_status CHECK (status IN ('SENT', 'FAILED'))
);
CREATE INDEX IF NOT EXISTS idx_em_alert_deliveries ON earthmoving_alert_deliveries (alert_id);
