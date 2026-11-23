-- Working-time limits per company and employee category, effective-dated. Rows are never edited or deleted:
-- a change is a new row with a later effective_from, so what applied in any past week can always be shown.
CREATE TABLE IF NOT EXISTS security_working_time_policies (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID         NOT NULL,
    category             VARCHAR(40)  NOT NULL,
    effective_from       DATE         NOT NULL,
    warn_at              NUMERIC(5,2) NOT NULL,
    ordinary_weekly      NUMERIC(5,2) NOT NULL,
    max_overtime_weekly  NUMERIC(5,2) NOT NULL,
    max_daily            NUMERIC(4,2) NOT NULL,
    max_daily_overtime   NUMERIC(4,2) NOT NULL,
    min_rest_hours       NUMERIC(4,2) NOT NULL,
    legal_basis          VARCHAR(500),
    created_by           UUID,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_working_time_policy UNIQUE (tenant_id, category, effective_from),
    CONSTRAINT chk_wtp_category CHECK (category ~ '^[A-Z0-9_]{1,40}$'),
    CONSTRAINT chk_wtp_ranges CHECK (
        ordinary_weekly > 0 AND ordinary_weekly <= 84 AND max_overtime_weekly >= 0 AND max_overtime_weekly <= 40
        AND warn_at > 0 AND warn_at <= ordinary_weekly
        AND max_daily > 0 AND max_daily <= 24 AND max_daily_overtime >= 0 AND max_daily_overtime <= max_daily
        AND min_rest_hours >= 0 AND min_rest_hours <= 24)
);
CREATE INDEX IF NOT EXISTS idx_wtp_lookup ON security_working_time_policies (tenant_id, category, effective_from DESC);

CREATE OR REPLACE FUNCTION security_working_time_policies_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'security_working_time_policies is append-only: add a new row with a later effective_from';
END; $$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_wtp_immutable ON security_working_time_policies;
CREATE TRIGGER trg_wtp_immutable BEFORE UPDATE OR DELETE ON security_working_time_policies
    FOR EACH ROW EXECUTE FUNCTION security_working_time_policies_immutable();

-- Which category each guard falls under. A guard with no row uses the company's DEFAULT policy.
CREATE TABLE IF NOT EXISTS security_guard_work_category (
    guard_id    UUID PRIMARY KEY,
    tenant_id   UUID        NOT NULL,
    category    VARCHAR(40) NOT NULL,
    updated_by  UUID,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_gwc_category CHECK (category ~ '^[A-Z0-9_]{1,40}$')
);
CREATE INDEX IF NOT EXISTS idx_gwc_tenant ON security_guard_work_category (tenant_id);
COMMENT ON TABLE security_working_time_policies IS 'Append-only, effective-dated working-time limits per company and employee category. DEFAULT is the company-wide fallback.';
