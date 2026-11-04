-- Patch 0571: per-person, per-event email settings for Projects notifications.
-- No row = the event's default (see projects PmEvent). The in-app bell is always written; this only controls email.
CREATE TABLE IF NOT EXISTS pm_notification_settings (
    tenant_id   UUID         NOT NULL,
    user_id     UUID         NOT NULL,
    event       VARCHAR(40)  NOT NULL,
    email       BOOLEAN      NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, user_id, event)
);

-- One overdue reminder per item per day, even if the sweep runs twice or on two instances.
CREATE TABLE IF NOT EXISTS pm_notification_sent (
    tenant_id   UUID         NOT NULL,
    event       VARCHAR(40)  NOT NULL,
    entity_id   UUID         NOT NULL,
    sent_on     DATE         NOT NULL,
    PRIMARY KEY (tenant_id, event, entity_id, sent_on)
);
