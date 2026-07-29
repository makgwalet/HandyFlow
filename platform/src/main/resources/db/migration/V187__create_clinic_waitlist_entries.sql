-- Rename this file to the actual next Flyway version before running
-- (see the "Migration versioning" note from earlier sessions — this
-- project's DB was last confirmed at v183+; check current head first).

CREATE TABLE clinic_waitlist_entries (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    patient_id        UUID NOT NULL,
    practitioner_id   UUID,
    appointment_type  VARCHAR(100),
    notes             TEXT,
    status            VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_clinic_waitlist_tenant_status ON clinic_waitlist_entries(tenant_id, status);