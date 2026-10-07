-- Clinic Sprint 1 (S1-6): who looked at which patient record, and when (POPIA accountability).
-- Append-only by convention: the application never updates or deletes rows.
CREATE TABLE clinic_access_log (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID         NOT NULL REFERENCES tenants(id),
    user_id       UUID,
    patient_id    UUID,
    resource_type VARCHAR(30)  NOT NULL,   -- PATIENT | CONSULTATION | LAB_RESULT | OTHER
    resource_id   UUID,
    http_method   VARCHAR(10)  NOT NULL,
    path          VARCHAR(300) NOT NULL,
    status_code   INTEGER,
    ip_address    VARCHAR(64),
    impersonated  BOOLEAN      NOT NULL DEFAULT FALSE,
    accessed_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_clinic_access_patient ON clinic_access_log (tenant_id, patient_id, accessed_at DESC);
CREATE INDEX idx_clinic_access_user    ON clinic_access_log (tenant_id, user_id, accessed_at DESC);
