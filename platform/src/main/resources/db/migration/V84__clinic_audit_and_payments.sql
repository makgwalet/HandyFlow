-- V84 — Clinic: consultation edit audit trail + payments table
-- WHY audit trail? ClinicConsultation has @Version for optimistic locking
-- but PATCH /consultations/{id} overwrites in place with no history.
-- HPCSA regulations treat clinical records as legal documents — "who changed
-- what and when" must be answerable. This table captures each version before
-- overwrite.

-- ── Consultation edit history ──────────────────────────────────────────────────

CREATE TABLE clinic_consultation_edits (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID         NOT NULL REFERENCES tenants(id),
    consultation_id  UUID         NOT NULL REFERENCES clinic_consultations(id) ON DELETE CASCADE,
    edited_by        UUID         REFERENCES users(id),
    edited_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    -- Snapshot of the fields that existed BEFORE this edit
    chief_complaint  TEXT,
    history          TEXT,
    examination      TEXT,
    diagnosis        TEXT,
    icd10_codes      TEXT[],
    treatment_plan   TEXT,
    follow_up_days   INTEGER,
    weight_kg        NUMERIC(6,2),
    height_cm        NUMERIC(5,1),
    blood_pressure   VARCHAR(20),
    pulse_bpm        INTEGER,
    temperature_c    NUMERIC(4,1),
    oxygen_sat_pct   NUMERIC(4,1),
    change_reason    TEXT         -- optional: doctor explains why they edited
);

CREATE INDEX idx_consult_edits_consultation ON clinic_consultation_edits(consultation_id);
CREATE INDEX idx_consult_edits_tenant       ON clinic_consultation_edits(tenant_id);

-- ── Payments table ──────────────────────────────────────────────────────────────
-- Enables: getPayments(), getRevenue(), accurate outstanding balance calculation.
-- Each row = one payment received (scheme EFT, patient cash/card, etc.)

CREATE TABLE clinic_payments (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL REFERENCES tenants(id),
    claim_id        UUID         REFERENCES clinic_claims(id),
    patient_id      UUID         REFERENCES clinic_patients(id),
    payment_method  VARCHAR(30)  NOT NULL DEFAULT 'CASH',
                                 -- CASH | CARD | EFT | SCHEME_EFT | MEDICAL_AID
    amount          NUMERIC(10,2) NOT NULL,
    reference       VARCHAR(100),  -- bank reference / scheme remittance number
    notes           TEXT,
    recorded_by     UUID         REFERENCES users(id),
    recorded_at     TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_payment_method CHECK (
        payment_method IN ('CASH','CARD','EFT','SCHEME_EFT','MEDICAL_AID'))
);

CREATE INDEX idx_clinic_payments_tenant    ON clinic_payments(tenant_id);
CREATE INDEX idx_clinic_payments_claim     ON clinic_payments(claim_id);
CREATE INDEX idx_clinic_payments_patient   ON clinic_payments(patient_id);
CREATE INDEX idx_clinic_payments_recorded  ON clinic_payments(recorded_at DESC);

-- ── Medical aid endpoint on patient ────────────────────────────────────────────
-- clinic_medical_aids already exists (V19). Add missing index only.
CREATE INDEX IF NOT EXISTS idx_clinic_medical_aids_patient
    ON clinic_medical_aids(patient_id, active);
