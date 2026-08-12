-- V<NEXT>__create_pos_settings.sql
CREATE TABLE pos_settings (
    id                              UUID PRIMARY KEY,
    tenant_id                       UUID NOT NULL UNIQUE,
    cash_variance_tolerance_amount  NUMERIC(15,2) NOT NULL DEFAULT 20.00,
    cash_variance_tolerance_pct     NUMERIC(5,4)  NOT NULL DEFAULT 0.0100,
    cash_variance_critical_amount   NUMERIC(15,2) NOT NULL DEFAULT 200.00,
    cash_variance_critical_pct      NUMERIC(5,4)  NOT NULL DEFAULT 0.0500,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_pos_settings_tenant ON pos_settings(tenant_id);