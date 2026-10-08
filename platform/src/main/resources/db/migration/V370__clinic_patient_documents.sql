-- Patient documents register (patch 0163): files uploaded from outside (letters, reports, imaging, paper notes) and
-- the sick notes and referral letters the clinic issues. The bytes live in the shared file storage; this table holds the
-- register entry. Nothing is deleted: a wrong or unwanted document is voided with a reason and drops out of the list.

CREATE TABLE IF NOT EXISTS clinic_patient_documents (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL REFERENCES tenants(id),
    patient_id      UUID         NOT NULL REFERENCES clinic_patients(id),
    consultation_id UUID,
    doc_type        VARCHAR(20)  NOT NULL CHECK (doc_type IN ('SICK_NOTE','REFERRAL','OUTSIDE_REPORT','IMAGING','LETTER','PAPER_NOTES','CONSENT_FORM','OTHER')),
    source          VARCHAR(10)  NOT NULL CHECK (source IN ('ISSUED','UPLOADED')),
    title           VARCHAR(200) NOT NULL,
    document_date   DATE         NOT NULL,
    notes           VARCHAR(1000),
    storage_key     VARCHAR(500) NOT NULL,
    file_name       VARCHAR(255) NOT NULL,
    content_type    VARCHAR(100) NOT NULL,
    size_bytes      BIGINT       NOT NULL CHECK (size_bytes > 0),
    created_by      UUID,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    voided_at       TIMESTAMP,
    voided_by       UUID,
    void_reason     VARCHAR(300),
    CHECK ((voided_at IS NULL) = (void_reason IS NULL))
);
CREATE INDEX IF NOT EXISTS idx_clinic_patient_documents_patient ON clinic_patient_documents (tenant_id, patient_id, document_date DESC);
