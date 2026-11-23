-- Sunday and public-holiday pay premiums, per company.
-- Off by default: a company that has not switched it on is paid exactly as before.
-- When on, hours worked on a Sunday are paid at sunday_multiplier and hours on a public holiday at holiday_multiplier (the holiday rate wins
-- when a day is both). The multipliers may not be below 1.50, the overtime rate, so an hour is never paid less for being on a premium day.
-- South Africa's public holidays are worked out in code; this table only holds the days a company adds (a day declared by proclamation) or removes.
CREATE TABLE IF NOT EXISTS security_pay_premium_settings (
    tenant_id          UUID          NOT NULL REFERENCES tenants(id),
    enabled            BOOLEAN       NOT NULL DEFAULT FALSE,
    sunday_multiplier  NUMERIC(4,2)  NOT NULL DEFAULT 1.50,
    holiday_multiplier NUMERIC(4,2)  NOT NULL DEFAULT 2.00,
    updated_by         UUID,
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_security_pay_premium_settings PRIMARY KEY (tenant_id),
    CONSTRAINT chk_pay_premium_sunday CHECK (sunday_multiplier BETWEEN 1.50 AND 4.00),
    CONSTRAINT chk_pay_premium_holiday CHECK (holiday_multiplier BETWEEN 1.50 AND 4.00)
);

CREATE TABLE IF NOT EXISTS security_public_holiday_overrides (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id    UUID         NOT NULL REFERENCES tenants(id),
    holiday_date DATE         NOT NULL,
    kind         VARCHAR(6)   NOT NULL,
    name         VARCHAR(100) NOT NULL,
    created_by   UUID,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_security_public_holiday_overrides PRIMARY KEY (id),
    CONSTRAINT chk_holiday_override_kind CHECK (kind IN ('ADD', 'REMOVE')),
    CONSTRAINT uq_holiday_override_day UNIQUE (tenant_id, holiday_date)
);
CREATE INDEX IF NOT EXISTS idx_holiday_overrides_tenant ON security_public_holiday_overrides (tenant_id, holiday_date);

-- Payroll lines for the premium hours: one per shift and type, like the others.
ALTER TABLE security_payroll_line_items DROP CONSTRAINT IF EXISTS security_payroll_line_items_line_type_check;
ALTER TABLE security_payroll_line_items ADD CONSTRAINT security_payroll_line_items_line_type_check
    CHECK (line_type IN ('REGULAR', 'OVERTIME', 'ALLOWANCE', 'DEDUCTION', 'SUNDAY', 'PUBLIC_HOLIDAY'));
