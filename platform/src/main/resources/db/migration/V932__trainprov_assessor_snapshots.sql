-- Training provider: a copy of who the assessor was, and what their appointment said, when an assessment was recorded or decided.
-- Practitioners and appointments can change later (a credential is re-verified, an appointment is withdrawn, a name is corrected). The snapshot keeps what
-- was true at the time. One row per subject and role, written once and never changed. subject_type also allows MODERATION and RPL for the patches that follow.
-- Rows made here for attempts that existed before this migration are flagged backfilled: they copy the current practitioner and appointment, so they show
-- what is on record now, not necessarily what was on record on the day, and carry no standing.
-- A decision made for an assessor who has no login says how the decision reached the person who recorded it.

CREATE TABLE trainprov_appointment_snapshots (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    subject_type         VARCHAR(20) NOT NULL CHECK (subject_type IN ('ATTEMPT', 'DECISION', 'MODERATION', 'RPL')),
    subject_id           UUID NOT NULL,
    role                 VARCHAR(20) NOT NULL CHECK (role IN ('FACILITATOR', 'ASSESSOR', 'MODERATOR')),
    practitioner_id      UUID NOT NULL REFERENCES trainprov_practitioners(id),
    practitioner_no      VARCHAR(30) NOT NULL,
    full_name            VARCHAR(310) NOT NULL,
    employment_type      VARCHAR(20) NOT NULL,
    has_login            BOOLEAN NOT NULL,
    appointment_id       UUID NOT NULL REFERENCES trainprov_practitioner_appointments(id),
    programme_id         UUID NOT NULL REFERENCES trainprov_programmes(id),
    authority_id         UUID REFERENCES trainprov_authorities(id),
    authority_name       VARCHAR(200),
    credential_ref       VARCHAR(100),
    valid_from           DATE,
    valid_to             DATE,
    verification_source  TEXT,
    verified_on          DATE,
    verified_by          UUID,
    standing             VARCHAR(20),
    standing_on          DATE NOT NULL,
    backfilled           BOOLEAN NOT NULL DEFAULT FALSE,
    captured_by          UUID NOT NULL,
    captured_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_trainprov_snapshot_subject UNIQUE (tenant_id, subject_type, subject_id, role),
    CONSTRAINT ck_trainprov_snapshot_standing CHECK (
        (standing IS NULL AND backfilled)
        OR COALESCE(standing IN ('VERIFIED_IN_FORCE', 'NOT_VERIFIED', 'NOT_YET_VALID', 'EXPIRED', 'WITHDRAWN'), FALSE)),
    CONSTRAINT ck_trainprov_snapshot_name CHECK (COALESCE(char_length(btrim(full_name)), 0) >= 2)
);
CREATE INDEX idx_trainprov_snapshot_practitioner ON trainprov_appointment_snapshots (tenant_id, practitioner_id, role);

INSERT INTO trainprov_appointment_snapshots (id, tenant_id, subject_type, subject_id, role, practitioner_id, practitioner_no, full_name, employment_type, has_login,
        appointment_id, programme_id, authority_id, authority_name, credential_ref, valid_from, valid_to, verification_source, verified_on, verified_by,
        standing, standing_on, backfilled, captured_by, captured_at)
SELECT gen_random_uuid(), a.tenant_id, 'ATTEMPT', a.id, 'ASSESSOR', p.id, p.practitioner_no, btrim(p.first_names || ' ' || p.surname), p.employment_type, p.user_id IS NOT NULL,
       ap.id, ap.programme_id, ap.authority_id, au.name, ap.credential_ref, ap.valid_from, ap.valid_to, ap.verification_source, ap.verified_on, ap.verified_by,
       NULL, a.assessed_on, TRUE, a.created_by, now()
  FROM trainprov_assessment_attempts a
  JOIN trainprov_practitioners p ON p.id = a.assessor_id
  JOIN trainprov_practitioner_appointments ap ON ap.id = a.appointment_id
  LEFT JOIN trainprov_authorities au ON au.id = ap.authority_id;

ALTER TABLE trainprov_assessment_decisions ADD COLUMN recorded_for_assessor TEXT;
ALTER TABLE trainprov_assessment_decisions ADD CONSTRAINT ck_trainprov_decision_for_assessor CHECK (
    recorded_for_assessor IS NULL OR char_length(btrim(recorded_for_assessor)) >= 10);
