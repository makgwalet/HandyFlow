-- Per alert type: may administrators be left off the routine notification? Default false, so every company behaves as before.
CREATE TABLE fuel_alert_topic_settings (
    tenant_id       UUID NOT NULL,
    topic           VARCHAR(30) NOT NULL,
    exclude_admins  BOOLEAN NOT NULL DEFAULT FALSE,
    updated_by      UUID,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, topic)
);
