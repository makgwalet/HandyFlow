-- Training provider: RPL assessors are checked like other practitioners.
-- 1. A new appointment role, RPL_ASSESSOR. An ordinary ASSESSOR appointment does not authorise recognition of prior learning: it is a separate
--    role with its own credential, programme and verification. Appointment snapshots may carry the role too.
-- 2. An RPL decision names the practitioner (assessor_id) and the appointment it was made under (assessor_appointment_id). Decisions made before this
--    migration have only a typed name; they are flagged legacy_assessor_text and are left as they are, not rewritten.
-- 3. An assessor with no login has the decision recorded for them: recorded_for_assessor says how it reached the person recording it.

ALTER TABLE trainprov_practitioner_appointments DROP CONSTRAINT trainprov_practitioner_appointments_role_check;
ALTER TABLE trainprov_practitioner_appointments ADD CONSTRAINT trainprov_practitioner_appointments_role_check
    CHECK (role IN ('FACILITATOR', 'ASSESSOR', 'MODERATOR', 'RPL_ASSESSOR'));
ALTER TABLE trainprov_appointment_snapshots DROP CONSTRAINT trainprov_appointment_snapshots_role_check;
ALTER TABLE trainprov_appointment_snapshots ADD CONSTRAINT trainprov_appointment_snapshots_role_check
    CHECK (role IN ('FACILITATOR', 'ASSESSOR', 'MODERATOR', 'RPL_ASSESSOR'));
-- An RPL assessor, like an assessor or moderator, cannot be marked verified without the credential reference that was verified.
ALTER TABLE trainprov_practitioner_appointments DROP CONSTRAINT ck_trainprov_appt_credential;
ALTER TABLE trainprov_practitioner_appointments ADD CONSTRAINT ck_trainprov_appt_credential CHECK (
    role = 'FACILITATOR' OR verified_on IS NULL OR COALESCE(char_length(btrim(credential_ref)), 0) >= 3);

ALTER TABLE trainprov_rpl_applications ADD COLUMN assessor_id UUID REFERENCES trainprov_practitioners(id);
ALTER TABLE trainprov_rpl_applications ADD COLUMN assessor_appointment_id UUID REFERENCES trainprov_practitioner_appointments(id);
ALTER TABLE trainprov_rpl_applications ADD COLUMN recorded_for_assessor TEXT;
ALTER TABLE trainprov_rpl_applications ADD COLUMN legacy_assessor_text BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE trainprov_rpl_applications SET legacy_assessor_text = TRUE WHERE assessor_name IS NOT NULL;
ALTER TABLE trainprov_rpl_applications ADD CONSTRAINT ck_trainprov_rpl_assessor_identity CHECK (
    assessor_name IS NULL
    OR legacy_assessor_text
    OR (assessor_id IS NOT NULL AND assessor_appointment_id IS NOT NULL));
ALTER TABLE trainprov_rpl_applications ADD CONSTRAINT ck_trainprov_rpl_for_assessor CHECK (
    recorded_for_assessor IS NULL OR char_length(btrim(recorded_for_assessor)) >= 10);
CREATE INDEX idx_trainprov_rpl_assessor ON trainprov_rpl_applications (tenant_id, assessor_id) WHERE assessor_id IS NOT NULL;
