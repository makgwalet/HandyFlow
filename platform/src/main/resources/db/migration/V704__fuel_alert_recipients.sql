-- Who else is told about a Fuel alert, besides the company's administrators. A company with no rows here behaves exactly as before.
CREATE TABLE fuel_alert_recipients (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    topic       VARCHAR(30) NOT NULL,
    user_id     UUID NOT NULL,
    created_by  UUID,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_fuel_alert_recipients ON fuel_alert_recipients (tenant_id, topic, user_id);
