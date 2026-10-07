-- Clinic Sprint 1 (DEC-CLINIC-004): nurse -> doctor handoff as a first-class state machine.
-- V338 is already applied, so its CHECK is replaced here rather than edited.
ALTER TABLE clinic_consultations DROP CONSTRAINT chk_clinic_consultation_status;
ALTER TABLE clinic_consultations
    ADD CONSTRAINT chk_clinic_consultation_status
    CHECK (status IN ('DRAFT','NURSE_IN_PROGRESS','READY_FOR_DOCTOR','DOCTOR_REVIEWING',
                      'RETURNED_TO_NURSE','DOCTOR_COMPLETED','SIGNED','LOCKED','ABANDONED'));

ALTER TABLE clinic_consultations
    ADD COLUMN reviewing_practitioner_id UUID REFERENCES clinic_practitioners(id);

CREATE INDEX idx_clinic_consultations_handoff
    ON clinic_consultations (tenant_id, updated_at)
    WHERE status IN ('READY_FOR_DOCTOR','DOCTOR_REVIEWING','DOCTOR_COMPLETED') AND deleted_at IS NULL;

-- Audit trail of every handoff move (who, when, why).
CREATE TABLE clinic_consultation_transitions (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL REFERENCES tenants(id),
    consultation_id UUID         NOT NULL REFERENCES clinic_consultations(id) ON DELETE CASCADE,
    from_status     VARCHAR(20)  NOT NULL,
    to_status       VARCHAR(20)  NOT NULL,
    actor_user_id   UUID         REFERENCES users(id),
    reason_code     VARCHAR(40),
    comment         TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_clinic_consult_transitions ON clinic_consultation_transitions (consultation_id, created_at);
