-- D1: shift variance controls. Everything here is optional; a company with no rows behaves exactly as before.

-- A cash-variance tolerance for one depot (site) or a gauge difference limit for one fuel type, instead of the company-wide number.
CREATE TABLE fuel_tolerance_overrides (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    scope       VARCHAR(10) NOT NULL CHECK (scope IN ('DEPOT','PRODUCT')),
    scope_key   VARCHAR(60) NOT NULL,
    amount      NUMERIC(14,2) NOT NULL CHECK (amount >= 0),
    updated_by  UUID,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_fuel_tolerance_overrides ON fuel_tolerance_overrides (tenant_id, scope, scope_key);

-- When the same attendant closes this many shifts outside tolerance within this many days, an investigation alert is raised. Off when absent.
CREATE TABLE fuel_shift_variance_policy (
    tenant_id     UUID PRIMARY KEY,
    repeat_count  INT NOT NULL CHECK (repeat_count >= 2),
    repeat_days   INT NOT NULL CHECK (repeat_days >= 1),
    updated_by    UUID,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Every shift accepted automatically: the variance, the tolerance in force, who (the system) and when. Append only.
CREATE TABLE fuel_shift_auto_accept_log (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    import_id         UUID NOT NULL,
    shift_id          UUID NOT NULL,
    shift_number      VARCHAR(40) NOT NULL,
    attendant_name    VARCHAR(120) NOT NULL,
    variance_amount   NUMERIC(14,2),
    tolerance_applied NUMERIC(10,2),
    actor             VARCHAR(20) NOT NULL DEFAULT 'SYSTEM',
    accepted_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_fuel_shift_auto_accept_log ON fuel_shift_auto_accept_log (tenant_id, accepted_at DESC);
