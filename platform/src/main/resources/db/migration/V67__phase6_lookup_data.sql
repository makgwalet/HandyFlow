-- V67__phase6_lookup_data.sql
-- Phase 6: Lookup Data Management
-- acc_public_holidays already exists from an earlier migration — we ADD the
-- missing year column if absent, then seed 2026 holidays.

-- ── SA Public Holidays ────────────────────────────────────────────────────────
-- Table already exists; add year column if the earlier migration didn't include it
ALTER TABLE acc_public_holidays ADD COLUMN IF NOT EXISTS year INT;

-- Back-fill year from holiday_date for any existing rows
UPDATE acc_public_holidays SET year = EXTRACT(YEAR FROM holiday_date) WHERE year IS NULL;

-- Seed 2026 SA public holidays (Public Holidays Act 36 of 1994)
INSERT INTO acc_public_holidays (id, holiday_date, name, year) VALUES
    (gen_random_uuid(), '2026-01-01', 'New Year''s Day',           2026),
    (gen_random_uuid(), '2026-03-21', 'Human Rights Day',           2026),
    (gen_random_uuid(), '2026-04-03', 'Good Friday',                2026),
    (gen_random_uuid(), '2026-04-06', 'Family Day',                 2026),
    (gen_random_uuid(), '2026-04-27', 'Freedom Day',                2026),
    (gen_random_uuid(), '2026-05-01', 'Workers'' Day',              2026),
    (gen_random_uuid(), '2026-06-16', 'Youth Day',                  2026),
    (gen_random_uuid(), '2026-08-09', 'National Women''s Day',      2026),
    (gen_random_uuid(), '2026-09-24', 'Heritage Day',               2026),
    (gen_random_uuid(), '2026-12-16', 'Day of Reconciliation',      2026),
    (gen_random_uuid(), '2026-12-25', 'Christmas Day',              2026),
    (gen_random_uuid(), '2026-12-26', 'Day of Goodwill',            2026)
ON CONFLICT (holiday_date) DO NOTHING;

-- ── Discount Codes ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS admin_discounts (
    id              UUID          NOT NULL DEFAULT gen_random_uuid(),
    code            VARCHAR(50)   NOT NULL,
    description     VARCHAR(255),
    discount_type   VARCHAR(10)   NOT NULL DEFAULT 'PERCENT'
        CHECK (discount_type IN ('PERCENT', 'FIXED')),
    value           NUMERIC(10,2) NOT NULL,
    applies_to      VARCHAR(20)   NOT NULL DEFAULT 'ALL'
        CHECK (applies_to IN ('ALL', 'PLAN', 'MODULE')),
    module_key      VARCHAR(50),
    valid_from      TIMESTAMP,
    valid_to        TIMESTAMP,
    max_uses        INT,
    uses_count      INT           NOT NULL DEFAULT 0,
    active          BOOLEAN       NOT NULL DEFAULT true,
    created_by      UUID,
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_admin_discounts     PRIMARY KEY (id),
    CONSTRAINT uq_admin_discount_code UNIQUE (code)
);

CREATE INDEX IF NOT EXISTS idx_admin_discounts_code   ON admin_discounts(code)   WHERE active = true;
CREATE INDEX IF NOT EXISTS idx_admin_discounts_active ON admin_discounts(active);

-- ── Module catalogue: admin columns ──────────────────────────────────────────
ALTER TABLE module_catalogue ADD COLUMN IF NOT EXISTS admin_notes TEXT;
ALTER TABLE module_catalogue ADD COLUMN IF NOT EXISTS is_active   BOOLEAN NOT NULL DEFAULT true;