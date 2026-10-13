-- Patch 0203, Document Studio:
-- (1) each issued document keeps its PDF (pdf_storage_key) and the record it was made from (source_ref: a visit id, a consent event id),
--     so downloading it again returns the same numbered copy instead of a new number;
-- (2) delivery is its own step (CLINIC-DEC-024): every time a document is emailed to the patient, one row here says who sent it, when and to which address.

ALTER TABLE clinic_issued_documents ADD COLUMN IF NOT EXISTS source_ref      VARCHAR(80);
ALTER TABLE clinic_issued_documents ADD COLUMN IF NOT EXISTS pdf_storage_key VARCHAR(500);
CREATE INDEX IF NOT EXISTS idx_clinic_issued_source ON clinic_issued_documents (tenant_id, doc_type, source_ref, issued_at DESC);

-- The two new columns are as fixed as the rest: an issued document can still only be voided, once.
CREATE OR REPLACE FUNCTION clinic_issued_documents_guard() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'clinic_issued_documents rows cannot be deleted';
    END IF;
    IF OLD.status = 'VOIDED' THEN
        RAISE EXCEPTION 'a voided document cannot be changed';
    END IF;
    IF NEW.id <> OLD.id OR NEW.tenant_id <> OLD.tenant_id OR NEW.doc_number <> OLD.doc_number OR NEW.doc_type <> OLD.doc_type
       OR NEW.patient_id <> OLD.patient_id OR NEW.consultation_id IS DISTINCT FROM OLD.consultation_id
       OR NEW.template_ref IS DISTINCT FROM OLD.template_ref OR NEW.letterhead_id IS DISTINCT FROM OLD.letterhead_id
       OR NEW.issuer_name <> OLD.issuer_name OR NEW.renderer <> OLD.renderer OR NEW.snapshot <> OLD.snapshot
       OR NEW.content_sha256 <> OLD.content_sha256 OR NEW.code_sha256 <> OLD.code_sha256
       OR NEW.issued_by IS DISTINCT FROM OLD.issued_by OR NEW.issued_at <> OLD.issued_at
       OR NEW.source_ref IS DISTINCT FROM OLD.source_ref OR NEW.pdf_storage_key IS DISTINCT FROM OLD.pdf_storage_key THEN
        RAISE EXCEPTION 'an issued document can only be voided, not edited';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE IF NOT EXISTS clinic_document_deliveries (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID         NOT NULL REFERENCES tenants(id),
    issued_document_id  UUID         NOT NULL REFERENCES clinic_issued_documents(id),
    channel             VARCHAR(10)  NOT NULL CHECK (channel IN ('EMAIL')),
    recipient           VARCHAR(255) NOT NULL,
    status              VARCHAR(10)  NOT NULL CHECK (status IN ('QUEUED','FAILED')),
    detail              VARCHAR(500),
    sent_by             UUID,
    sent_at             TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_clinic_doc_deliveries_doc ON clinic_document_deliveries (tenant_id, issued_document_id, sent_at DESC);

CREATE OR REPLACE FUNCTION clinic_document_deliveries_guard() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'clinic_document_deliveries is append-only';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_clinic_document_deliveries_guard ON clinic_document_deliveries;
CREATE TRIGGER trg_clinic_document_deliveries_guard
    BEFORE UPDATE OR DELETE ON clinic_document_deliveries FOR EACH ROW EXECUTE FUNCTION clinic_document_deliveries_guard();
