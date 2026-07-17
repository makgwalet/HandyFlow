-- ═══════════════════════════════════════════════════════════════════════════
-- IMPORTANT: rename this file before running it.
-- "V999" is a placeholder — replace it with your project's actual next
-- sequential Flyway version number.
-- ═══════════════════════════════════════════════════════════════════════════
--
-- New table for supplier invoice attachments — base64-in-DB, following
-- Creative's own proven working pattern (cre_proofs/cre_deliverables) since
-- there's no S3/object storage available in this environment yet. See
-- ScSupplierInvoiceAttachment.java's class Javadoc for the full reasoning,
-- including the two real gaps in Creative's version fixed here (honest
-- column naming instead of a misleading "file_url" for content that isn't
-- a URL, and an actual file-size cap enforced in the service layer).
--
-- This is a known, deliberate limitation to revisit once real object
-- storage exists — not a permanent architecture decision.

CREATE TABLE sc_supplier_invoice_attachments (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    supplier_invoice_id   UUID NOT NULL REFERENCES sc_supplier_invoices(id),
    file_name             VARCHAR(255) NOT NULL,
    content_type          VARCHAR(100) NOT NULL,
    file_size_bytes       BIGINT NOT NULL,
    file_content_base64   TEXT NOT NULL,
    uploaded_by           UUID,
    uploaded_by_name      VARCHAR(255),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Backs findSummariesByInvoice() — every list call is scoped to
-- (tenant_id, supplier_invoice_id).
CREATE INDEX idx_sc_supplier_invoice_attachments_invoice
    ON sc_supplier_invoice_attachments (tenant_id, supplier_invoice_id);