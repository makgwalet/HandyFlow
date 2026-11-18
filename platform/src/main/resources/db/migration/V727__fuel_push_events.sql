-- Fuel push alerts: one row per alert per person, so the same event is never sent twice, delivery and failure can be seen,
-- failures are retried, and a person can switch an event type off. Additive only.
CREATE TABLE fuel_push_events (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    event_type      VARCHAR(30) NOT NULL,
    dedup_key       VARCHAR(200) NOT NULL,
    user_id         UUID NOT NULL,
    title           VARCHAR(120) NOT NULL,
    body            VARCHAR(300) NOT NULL,
    data_json       TEXT NOT NULL DEFAULT '{}',
    status          VARCHAR(10) NOT NULL,
    attempts        INT NOT NULL DEFAULT 0,
    last_error      VARCHAR(300),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at         TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ,
    CONSTRAINT chk_fuel_push_event_status CHECK (status IN ('PENDING','SENT','FAILED','GAVE_UP','NO_DEVICE','SKIPPED'))
);
-- The same event to the same person once, however many times it is published.
CREATE UNIQUE INDEX uq_fuel_push_event_dedup ON fuel_push_events (tenant_id, dedup_key, user_id);
CREATE INDEX ix_fuel_push_event_due ON fuel_push_events (next_attempt_at) WHERE status IN ('PENDING','FAILED');

CREATE TABLE fuel_push_preferences (
    tenant_id  UUID NOT NULL,
    user_id    UUID NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    enabled    BOOLEAN NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, user_id, event_type)
);

-- Overdue delivery policy: the driver is told once the load is this many minutes past its scheduled time; dispatch is told
-- this many minutes after that, if it is still open.
ALTER TABLE fuel_settings ADD COLUMN IF NOT EXISTS overdue_grace_minutes INT NOT NULL DEFAULT 120;
ALTER TABLE fuel_settings ADD COLUMN IF NOT EXISTS overdue_escalation_minutes INT NOT NULL DEFAULT 60;
