-- Warehousing: contracts may override the standard storage billable-day rule. The standard (no row) is every calendar day, inclusive:
-- arrival day, dispatch day, weekends and public holidays all bill, and a part day bills as a full day. A rule here replaces that for one client
-- (client_id set) or as the warehouse default (client_id NULL), from effective_from. Rules are versions: a newer one supersedes, nothing is edited,
-- so an old invoice can still be explained.
CREATE TABLE IF NOT EXISTS whse_billing_day_rules (
    id             UUID PRIMARY KEY,
    tenant_id      UUID        NOT NULL,
    client_id      UUID REFERENCES whse_clients (id),
    day_rule       VARCHAR(30) NOT NULL CHECK (day_rule IN ('ALL_DAYS','WEEKDAYS_ONLY','WEEKDAYS_NO_HOLIDAYS')),
    effective_from DATE        NOT NULL,
    note           VARCHAR(300),
    created_by     UUID        NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_whse_day_rules_lookup ON whse_billing_day_rules (tenant_id, client_id, effective_from DESC);

-- Public holidays a tenant's WEEKDAYS_NO_HOLIDAYS rules skip. Kept by the warehouse; HandyFlow does not guess them.
CREATE TABLE IF NOT EXISTS whse_billing_holidays (
    tenant_id    UUID        NOT NULL,
    holiday_date DATE        NOT NULL,
    name         VARCHAR(80) NOT NULL CHECK (length(btrim(name)) > 0),
    created_by   UUID        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, holiday_date)
);
