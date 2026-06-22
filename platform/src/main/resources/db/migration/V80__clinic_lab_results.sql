-- WHY a lab results inbox?
-- Labs (Ampath, Lancet, Pathcare) email PDFs to the practice.
-- Current workflow: receptionist downloads, manually files. This table captures
-- the inbound result, links to a patient, and stores parsed markers in JSONB.
-- Plain-language interpretation is added by Claude API when the doctor reviews.

CREATE TABLE clinic_lab_results (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    patient_id      UUID REFERENCES clinic_patients(id),     -- NULL until matched
    consultation_id UUID REFERENCES clinic_consultations(id), -- NULL until filed
    -- Source
    source          VARCHAR(50) NOT NULL DEFAULT 'MANUAL'
        CHECK (source IN ('AMPATH','LANCET','PATHCARE','VERMAAK','EMAIL','MANUAL')),
    lab_reference   VARCHAR(100),   -- lab's own reference number
    collected_at    TIMESTAMP,      -- specimen collection date/time
    received_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    -- Document
    pdf_url         TEXT,           -- S3/storage URL of the PDF
    pdf_filename    VARCHAR(200),
    -- Status
    status          VARCHAR(20) NOT NULL DEFAULT 'UNREVIEWED'
        CHECK (status IN ('UNREVIEWED','REVIEWED','FILED','REJECTED')),
    reviewed_by     UUID REFERENCES clinic_practitioners(id),
    reviewed_at     TIMESTAMP,
    -- Parsed data
    patient_name_raw VARCHAR(200),  -- extracted from PDF before matching
    parsed_markers  JSONB,          -- [{marker, value, unit, ref_range, flag: HIGH/LOW/NORMAL}]
    interpretation  TEXT,           -- Claude-generated plain-language summary
    -- Notification
    notified        BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_clinic_lab_results_tenant  ON clinic_lab_results(tenant_id);
CREATE INDEX idx_clinic_lab_results_patient ON clinic_lab_results(patient_id) WHERE patient_id IS NOT NULL;
CREATE INDEX idx_clinic_lab_results_status  ON clinic_lab_results(tenant_id, status);
