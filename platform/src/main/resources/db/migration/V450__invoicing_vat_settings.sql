-- Company VAT setting, effective-dated. Each row is a change that takes effect on a date; the setting on any day is the latest row
-- whose date has come. Rows are never edited: a change is a new row, and a change that has not started yet can be cancelled. Documents
-- already created keep the rate stored on them, so a later change never rewrites an invoice or a charge. A company with no row is
-- treated as VAT-registered at the platform's standard rate, exactly as before this table existed.
CREATE TABLE IF NOT EXISTS invoicing_vat_settings (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    vat_registered  BOOLEAN NOT NULL,
    standard_rate   NUMERIC(5,2) NOT NULL CHECK (standard_rate >= 0 AND standard_rate <= 100),
    effective_from  DATE NOT NULL,
    reason          VARCHAR(300),
    created_by      UUID,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    cancelled_at    TIMESTAMP,
    CONSTRAINT chk_inv_vat_unregistered_zero CHECK (vat_registered OR standard_rate = 0),
    CONSTRAINT chk_inv_vat_registered_rate CHECK (NOT vat_registered OR standard_rate > 0)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_inv_vat_settings_date ON invoicing_vat_settings (tenant_id, effective_from) WHERE cancelled_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_inv_vat_settings_tenant ON invoicing_vat_settings (tenant_id, effective_from DESC);
