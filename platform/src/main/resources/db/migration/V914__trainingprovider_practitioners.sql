-- Training Provider: practitioners (facilitators, assessors, moderators) and their appointments.
-- An appointment is per role and per programme. The credential reference is recorded as the person typed it; it
-- counts as verified only when someone has recorded where it was checked. Nothing is deleted: practitioners are
-- deactivated and appointments withdrawn, each with a reason. No registration numbers or credentials are seeded.

CREATE TABLE trainprov_practitioners (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    practitioner_no     VARCHAR(30) NOT NULL,
    first_names         VARCHAR(150) NOT NULL,
    surname             VARCHAR(150) NOT NULL,
    email               VARCHAR(200),
    phone               VARCHAR(50),
    employment_type     VARCHAR(20) NOT NULL DEFAULT 'EMPLOYEE' CHECK (employment_type IN ('EMPLOYEE', 'CONTRACTOR', 'SECONDED')),
    user_id             UUID,
    notes               TEXT,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    inactive_reason     TEXT,
    inactive_by         UUID,
    inactive_at         TIMESTAMPTZ,
    created_by          UUID NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_trainprov_practitioner_no UNIQUE (tenant_id, practitioner_no),
    CONSTRAINT ck_trainprov_practitioner_inactive CHECK (
        (status = 'ACTIVE' AND inactive_reason IS NULL AND inactive_by IS NULL AND inactive_at IS NULL)
        OR (status = 'INACTIVE' AND COALESCE(char_length(btrim(inactive_reason)), 0) >= 10 AND inactive_by IS NOT NULL AND inactive_at IS NOT NULL))
);
CREATE INDEX idx_trainprov_practitioner_tenant ON trainprov_practitioners (tenant_id, status, surname);

CREATE TABLE trainprov_practitioner_appointments (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    practitioner_id      UUID NOT NULL REFERENCES trainprov_practitioners(id),
    role                 VARCHAR(20) NOT NULL CHECK (role IN ('FACILITATOR', 'ASSESSOR', 'MODERATOR')),
    programme_id         UUID NOT NULL REFERENCES trainprov_programmes(id),
    authority_id         UUID REFERENCES trainprov_authorities(id),
    credential_ref       VARCHAR(100),
    credential_note      TEXT,
    valid_from           DATE,
    valid_to             DATE,
    verification_source  TEXT,
    verified_on          DATE,
    verified_by          UUID,
    status               VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'WITHDRAWN')),
    withdrawn_reason     TEXT,
    withdrawn_by         UUID,
    withdrawn_at         TIMESTAMPTZ,
    created_by           UUID NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    version              BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_trainprov_appt_dates CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_to >= valid_from),
    CONSTRAINT ck_trainprov_appt_verified CHECK (
        (verification_source IS NULL AND verified_on IS NULL AND verified_by IS NULL)
        OR (COALESCE(char_length(btrim(verification_source)), 0) >= 5 AND verified_on IS NOT NULL AND verified_by IS NOT NULL)),
    -- An assessor or moderator cannot be marked verified without the credential reference that was verified.
    CONSTRAINT ck_trainprov_appt_credential CHECK (
        role = 'FACILITATOR' OR verified_on IS NULL OR COALESCE(char_length(btrim(credential_ref)), 0) >= 3),
    CONSTRAINT ck_trainprov_appt_withdrawn CHECK (
        (status = 'ACTIVE' AND withdrawn_reason IS NULL AND withdrawn_by IS NULL AND withdrawn_at IS NULL)
        OR (status = 'WITHDRAWN' AND COALESCE(char_length(btrim(withdrawn_reason)), 0) >= 10 AND withdrawn_by IS NOT NULL AND withdrawn_at IS NOT NULL))
);
-- One live appointment per person, role and programme.
CREATE UNIQUE INDEX uq_trainprov_appt_live ON trainprov_practitioner_appointments (tenant_id, practitioner_id, role, programme_id)
    WHERE status = 'ACTIVE';
CREATE INDEX idx_trainprov_appt_practitioner ON trainprov_practitioner_appointments (tenant_id, practitioner_id);
CREATE INDEX idx_trainprov_appt_programme ON trainprov_practitioner_appointments (tenant_id, programme_id, role, status);

-- Immutable history of an appointment: made, verified (each time), withdrawn.
CREATE TABLE trainprov_practitioner_appointment_events (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    appointment_id  UUID NOT NULL REFERENCES trainprov_practitioner_appointments(id),
    action          VARCHAR(20) NOT NULL CHECK (action IN ('CREATED', 'VERIFIED', 'WITHDRAWN', 'CHANGED')),
    note            TEXT,
    acted_by        UUID NOT NULL,
    acted_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_trainprov_appt_events ON trainprov_practitioner_appointment_events (tenant_id, appointment_id, acted_at);
