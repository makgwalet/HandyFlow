-- Compliance Services: the practice's price book. One numbered series per practice. A version is written as a draft,
-- reviewed by a second person and, once approved, never changed: a change is a new version. A quote prices on the
-- version in force on its date, so an accepted quote keeps its version. Prices are ex VAT. Government fees and other
-- disbursements are never part of the book; they are added to a quote at cost.

CREATE TABLE IF NOT EXISTS compliance_price_book_versions (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    version_number    INTEGER NOT NULL,
    review_status     VARCHAR(12) NOT NULL DEFAULT 'DRAFT',     -- DRAFT, IN_REVIEW, APPROVED
    currency          VARCHAR(3) NOT NULL DEFAULT 'ZAR',
    label             VARCHAR(120),
    change_summary    TEXT,
    authored_by       UUID,
    submitted_by      UUID,
    submitted_at      TIMESTAMPTZ,
    reviewed_by       UUID,
    reviewed_at       TIMESTAMPTZ,
    review_note       TEXT,
    review_exception_reason TEXT,
    effective_from    DATE,
    effective_to      DATE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_price_book_version UNIQUE (tenant_id, version_number),
    CONSTRAINT ck_price_book_status CHECK (review_status IN ('DRAFT', 'IN_REVIEW', 'APPROVED')),
    CONSTRAINT ck_price_book_effective CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_price_book_approved_dated CHECK (review_status <> 'APPROVED' OR effective_from IS NOT NULL)
);
-- one open draft or review per practice
CREATE UNIQUE INDEX IF NOT EXISTS uq_price_book_open_draft
    ON compliance_price_book_versions(tenant_id) WHERE review_status IN ('DRAFT', 'IN_REVIEW');

CREATE TABLE IF NOT EXISTS compliance_price_book_lines (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    version_id        UUID NOT NULL REFERENCES compliance_price_book_versions(id) ON DELETE CASCADE,
    service_code      VARCHAR(60) NOT NULL,
    pricing_model     VARCHAR(20) NOT NULL,
    base_fee          NUMERIC(12,2),
    unit_rate         NUMERIC(12,2),
    hourly_grade      VARCHAR(30),
    min_fee           NUMERIC(12,2),
    urgent_surcharge_pct NUMERIC(5,2),
    tax_treatment     VARCHAR(12) NOT NULL DEFAULT 'STANDARD',  -- STANDARD, ZERO_RATED, EXEMPT
    notes             VARCHAR(500),
    sort_order        INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_price_book_line UNIQUE (version_id, service_code),
    CONSTRAINT ck_price_line_tax CHECK (tax_treatment IN ('STANDARD', 'ZERO_RATED', 'EXEMPT')),
    CONSTRAINT ck_price_line_amounts CHECK ((base_fee IS NULL OR base_fee >= 0) AND (unit_rate IS NULL OR unit_rate >= 0) AND (min_fee IS NULL OR min_fee >= 0)),
    CONSTRAINT ck_price_line_urgent CHECK (urgent_surcharge_pct IS NULL OR (urgent_surcharge_pct >= 0 AND urgent_surcharge_pct <= 200))
);
CREATE INDEX IF NOT EXISTS idx_price_book_lines ON compliance_price_book_lines(version_id);

CREATE TABLE IF NOT EXISTS compliance_price_book_rates (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    version_id  UUID NOT NULL REFERENCES compliance_price_book_versions(id) ON DELETE CASCADE,
    grade       VARCHAR(30) NOT NULL,
    hourly_rate NUMERIC(12,2) NOT NULL,
    cost_rate   NUMERIC(12,2),                                  -- what an hour costs the practice; shown only to administrators
    sort_order  INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_price_book_rate UNIQUE (version_id, grade),
    CONSTRAINT ck_price_rate_amounts CHECK (hourly_rate >= 0 AND (cost_rate IS NULL OR cost_rate >= 0))
);
CREATE INDEX IF NOT EXISTS idx_price_book_rates ON compliance_price_book_rates(version_id);
