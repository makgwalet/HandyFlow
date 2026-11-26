-- Training provider: learnership agreements. An agreement ties a learner's enrolment to an employer and records the parties, the planned dates, who signed
-- and when, and a registration reference exactly as staff entered it from the paper agreement. It is a record of that agreement. It does not verify the
-- reference against any register, never says the learnership is registered or approved, and holds no rates, grants or eligibility rules.
-- Nothing is deleted: an agreement ends or is cancelled with a reason.

CREATE TABLE trainprov_learnership_agreements (
    id                       UUID PRIMARY KEY,
    tenant_id                UUID NOT NULL,
    agreement_no             VARCHAR(30) NOT NULL,
    enrolment_id             UUID NOT NULL REFERENCES trainprov_learner_enrolments(id),
    client_id                UUID REFERENCES trainprov_clients(id),
    employer_name            VARCHAR(200) NOT NULL,
    employer_contact         VARCHAR(300),
    start_date               DATE NOT NULL,
    end_date                 DATE,
    status                   VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'SIGNED', 'ENDED', 'CANCELLED')),
    learner_signed_on        DATE,
    employer_signed_on       DATE,
    provider_signed_on       DATE,
    signing_recorded_by      UUID,
    registration_ref         VARCHAR(100),
    registration_recorded_on DATE,
    registration_recorded_by UUID,
    ended_on                 DATE,
    end_reason               TEXT,
    cancel_reason            TEXT,
    created_by               UUID NOT NULL,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                  BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_trainprov_learnership_no UNIQUE (tenant_id, agreement_no),
    CONSTRAINT ck_trainprov_learnership_employer CHECK (COALESCE(char_length(btrim(employer_name)), 0) >= 2),
    CONSTRAINT ck_trainprov_learnership_dates CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT ck_trainprov_learnership_signing CHECK (
        (learner_signed_on IS NULL AND employer_signed_on IS NULL AND provider_signed_on IS NULL AND signing_recorded_by IS NULL)
        OR (learner_signed_on IS NOT NULL AND employer_signed_on IS NOT NULL AND provider_signed_on IS NOT NULL AND signing_recorded_by IS NOT NULL)),
    CONSTRAINT ck_trainprov_learnership_registration CHECK (
        (registration_ref IS NULL AND registration_recorded_on IS NULL AND registration_recorded_by IS NULL)
        OR (COALESCE(char_length(btrim(registration_ref)), 0) >= 3 AND registration_recorded_on IS NOT NULL AND registration_recorded_by IS NOT NULL
            AND learner_signed_on IS NOT NULL AND status IN ('SIGNED', 'ENDED'))),
    CONSTRAINT ck_trainprov_learnership_status CHECK (
        (status = 'DRAFT' AND learner_signed_on IS NULL AND ended_on IS NULL AND end_reason IS NULL AND cancel_reason IS NULL)
        OR (status = 'SIGNED' AND learner_signed_on IS NOT NULL AND ended_on IS NULL AND end_reason IS NULL AND cancel_reason IS NULL)
        OR (status = 'ENDED' AND learner_signed_on IS NOT NULL AND ended_on IS NOT NULL AND COALESCE(char_length(btrim(end_reason)), 0) >= 10 AND cancel_reason IS NULL)
        OR (status = 'CANCELLED' AND ended_on IS NULL AND end_reason IS NULL AND COALESCE(char_length(btrim(cancel_reason)), 0) >= 10 AND registration_ref IS NULL))
);
-- One live agreement per enrolment.
CREATE UNIQUE INDEX uq_trainprov_learnership_live ON trainprov_learnership_agreements (tenant_id, enrolment_id) WHERE status IN ('DRAFT', 'SIGNED');
CREATE INDEX idx_trainprov_learnership_enrolment ON trainprov_learnership_agreements (tenant_id, enrolment_id);
