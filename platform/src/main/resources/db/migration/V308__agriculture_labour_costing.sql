-- V308__agriculture_labour_costing.sql
--
-- ADR-001, W3: labour cost from HR. Two things.
--
-- 1. ag_finance_settings: one row per tenant holding the two numbers labour costing needs. standard_hours_per_week turns an HR salary (which is
--    per pay period) into an hourly rate (the BCEA ordinary maximum of 45 is the default); labour_on_cost_percent loads that rate with the
--    employer's on-costs (UIF, SDL and so on). With no row the defaults apply, and reading never creates one.
--
-- 2. A guarantee that one piece of work cannot be costed twice. Labour is costed per work record (an input application or a harvest) and the
--    ledger entry points back at it with source_ref. The application checks first; this unique index makes it hold even when two people press
--    "cost" at the same moment. It covers ACTIVE labour entries only, so a record whose cost was reversed can be costed again.
CREATE TABLE ag_finance_settings (
    id                       UUID PRIMARY KEY,
    tenant_id                UUID NOT NULL,
    standard_hours_per_week  NUMERIC(5,2) NOT NULL DEFAULT 45,
    labour_on_cost_percent   NUMERIC(5,2) NOT NULL DEFAULT 0,
    updated_by               UUID,
    created_at               TIMESTAMPTZ NOT NULL,
    updated_at               TIMESTAMPTZ NOT NULL,
    version                  BIGINT,
    CONSTRAINT uq_ag_finance_settings_tenant UNIQUE (tenant_id),
    CONSTRAINT chk_ag_fin_hours  CHECK (standard_hours_per_week > 0 AND standard_hours_per_week <= 84),
    CONSTRAINT chk_ag_fin_oncost CHECK (labour_on_cost_percent >= 0 AND labour_on_cost_percent <= 100)
);

CREATE UNIQUE INDEX uq_ag_cost_entries_labour_source
    ON ag_cost_entries (tenant_id, source_ref)
    WHERE source_type = 'HR_LABOUR' AND status = 'ACTIVE';
