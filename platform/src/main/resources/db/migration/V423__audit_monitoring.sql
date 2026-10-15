-- A22: continuous controls monitoring. The system looks for patterns that deserve a person's attention (duplicates, unusual journals, conflicting
-- permissions) and files an alert. An alert is only ever closed by a person, who must say why. The same pattern is never filed twice (fingerprint).
CREATE TABLE audit_monitor_alerts (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    rule           VARCHAR(30) NOT NULL,
    severity       VARCHAR(6) NOT NULL,              -- HIGH | MEDIUM | LOW
    subject_type   VARCHAR(20) NOT NULL,             -- JOURNAL | BANK_TRANSACTION | USER
    subject_ids    TEXT NOT NULL,                    -- comma-separated ids of what was flagged
    involved_users TEXT,                             -- comma-separated ids of people involved; they may not review the alert
    fingerprint    VARCHAR(200) NOT NULL,
    summary        VARCHAR(500) NOT NULL,
    detail         TEXT,
    amount         NUMERIC(19,2),
    occurred_on    DATE,
    status         VARCHAR(10) NOT NULL DEFAULT 'NEW',   -- NEW | IN_REVIEW | DISMISSED | ESCALATED
    first_seen     TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen      TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_by    UUID,
    reviewed_by_name VARCHAR(255),
    reviewed_at    TIMESTAMPTZ,
    disposition    TEXT,
    finding_id     UUID,
    CONSTRAINT uq_audit_monitor_alert UNIQUE (tenant_id, fingerprint),
    CONSTRAINT ck_audit_monitor_status CHECK (status IN ('NEW','IN_REVIEW','DISMISSED','ESCALATED')),
    CONSTRAINT ck_audit_monitor_severity CHECK (severity IN ('HIGH','MEDIUM','LOW')),
    CONSTRAINT ck_audit_monitor_closed CHECK (status NOT IN ('DISMISSED','ESCALATED') OR (disposition IS NOT NULL AND reviewed_by IS NOT NULL))
);
CREATE INDEX idx_audit_monitor_alerts_status ON audit_monitor_alerts (tenant_id, status, severity);
