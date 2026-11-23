-- A saved, frozen copy of the billing preview for one site and month, that a second person can approve. It is NOT an invoice:
-- it has no invoice number and nothing is sent anywhere. It is what the numbers were when someone said "bill this".
--   DRAFT    - saved; can be approved or voided
--   APPROVED - checked; frozen; can still be voided (with a reason) if the invoice is cancelled
--   VOID     - cancelled; kept for the record; a new draft may then be made for the same site and month
-- Rows are never deleted. snapshot holds the complete preview as it was; the summary columns are copied out for rules and lists.
CREATE TABLE IF NOT EXISTS security_invoice_drafts (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id      UUID         NOT NULL REFERENCES tenants(id),
    site_id        UUID         NOT NULL REFERENCES security_sites(id),
    month          VARCHAR(7)   NOT NULL,
    basis          VARCHAR(10)  NOT NULL,
    currency       CHAR(3)      NOT NULL DEFAULT 'ZAR',
    status         VARCHAR(10)  NOT NULL DEFAULT 'DRAFT',
    total_cents    BIGINT       NOT NULL,
    in_progress    BOOLEAN      NOT NULL,
    unpriced_hours NUMERIC(10,2) NOT NULL DEFAULT 0,
    mixed_vat      BOOLEAN      NOT NULL DEFAULT FALSE,
    snapshot       JSONB        NOT NULL,
    note           VARCHAR(500),
    created_by     UUID,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    approved_by    UUID,
    approved_at    TIMESTAMPTZ,
    voided_by      UUID,
    voided_at      TIMESTAMPTZ,
    void_reason    VARCHAR(500),
    CONSTRAINT pk_security_invoice_drafts PRIMARY KEY (id),
    CONSTRAINT chk_invoice_drafts_status CHECK (status IN ('DRAFT', 'APPROVED', 'VOID')),
    CONSTRAINT chk_invoice_drafts_basis CHECK (basis IN ('SCHEDULED', 'ACTUAL')),
    CONSTRAINT chk_invoice_drafts_month CHECK (month ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    CONSTRAINT chk_invoice_drafts_total CHECK (total_cents >= 0),
    CONSTRAINT chk_invoice_drafts_approved CHECK ((status = 'APPROVED') = (approved_at IS NOT NULL AND approved_by IS NOT NULL) OR status = 'VOID'),
    CONSTRAINT chk_invoice_drafts_void CHECK ((status = 'VOID') = (voided_at IS NOT NULL AND void_reason IS NOT NULL))
);
-- one live (draft or approved) invoice draft per site and month
CREATE UNIQUE INDEX IF NOT EXISTS uq_security_invoice_drafts_live ON security_invoice_drafts (tenant_id, site_id, month) WHERE status <> 'VOID';
CREATE INDEX IF NOT EXISTS idx_security_invoice_drafts_site ON security_invoice_drafts (tenant_id, site_id, month DESC);
COMMENT ON TABLE security_invoice_drafts IS 'Frozen billing preview per site and month awaiting approval. Not an invoice. Never deleted.';
