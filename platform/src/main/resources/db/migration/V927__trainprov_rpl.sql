-- Training provider: recognition of prior learning (RPL). An application is recorded against an enrolment, optionally for one module, with the evidence
-- the learner offers. Each piece of evidence is reviewed. A decision is recorded with a written basis and the assessor's name, then confirmed by a second
-- person, or the decider records a written reason for confirming alone. Nothing is deleted: evidence and applications are withdrawn with a reason.
-- An RPL decision is its own record. It does not feed readiness, completion or any certificate.

CREATE TABLE trainprov_rpl_applications (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    rpl_no            VARCHAR(30) NOT NULL,
    enrolment_id      UUID NOT NULL REFERENCES trainprov_learner_enrolments(id),
    module_id         UUID REFERENCES trainprov_programme_modules(id),
    claim             TEXT NOT NULL,
    applied_on        DATE NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'DECIDED', 'CONFIRMED', 'WITHDRAWN')),
    outcome           VARCHAR(30) CHECK (outcome IN ('RECOGNISED', 'PARTLY_RECOGNISED', 'NOT_RECOGNISED')),
    decision_basis    TEXT,
    assessor_name     VARCHAR(200),
    decided_on        DATE,
    decided_by        UUID,
    single_reason     TEXT,
    confirmed_on      DATE,
    confirmed_by      UUID,
    withdraw_reason   TEXT,
    withdrawn_by      UUID,
    created_by        UUID NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_trainprov_rpl_no UNIQUE (tenant_id, rpl_no),
    CONSTRAINT ck_trainprov_rpl_claim CHECK (COALESCE(char_length(btrim(claim)), 0) >= 10),
    CONSTRAINT ck_trainprov_rpl_state CHECK (
        (status = 'OPEN' AND outcome IS NULL AND decision_basis IS NULL AND assessor_name IS NULL AND decided_on IS NULL AND decided_by IS NULL
            AND single_reason IS NULL AND confirmed_on IS NULL AND confirmed_by IS NULL AND withdraw_reason IS NULL)
        OR (status = 'DECIDED' AND outcome IS NOT NULL AND COALESCE(char_length(btrim(decision_basis)), 0) >= 10 AND COALESCE(char_length(btrim(assessor_name)), 0) >= 2
            AND decided_on IS NOT NULL AND decided_by IS NOT NULL AND single_reason IS NULL AND confirmed_on IS NULL AND confirmed_by IS NULL AND withdraw_reason IS NULL)
        OR (status = 'CONFIRMED' AND outcome IS NOT NULL AND COALESCE(char_length(btrim(decision_basis)), 0) >= 10 AND COALESCE(char_length(btrim(assessor_name)), 0) >= 2
            AND decided_on IS NOT NULL AND decided_by IS NOT NULL AND withdraw_reason IS NULL
            AND ((single_reason IS NULL AND confirmed_by IS NOT NULL AND confirmed_on IS NOT NULL AND confirmed_by <> decided_by AND confirmed_on >= decided_on)
                 OR (COALESCE(char_length(btrim(single_reason)), 0) >= 10 AND confirmed_by IS NULL AND confirmed_on IS NULL)))
        OR (status = 'WITHDRAWN' AND COALESCE(char_length(btrim(withdraw_reason)), 0) >= 10 AND withdrawn_by IS NOT NULL))
);
-- One live application per enrolment and module (module null counts as the whole programme).
CREATE UNIQUE INDEX uq_trainprov_rpl_live ON trainprov_rpl_applications (tenant_id, enrolment_id, COALESCE(module_id, '00000000-0000-0000-0000-000000000000'::uuid))
    WHERE status <> 'WITHDRAWN';
CREATE INDEX idx_trainprov_rpl_enrolment ON trainprov_rpl_applications (tenant_id, enrolment_id);

CREATE TABLE trainprov_rpl_evidence (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    application_id   UUID NOT NULL REFERENCES trainprov_rpl_applications(id),
    kind             VARCHAR(20) NOT NULL CHECK (kind IN ('DOCUMENT', 'REFERENCE', 'WORK_SAMPLE', 'INTERVIEW', 'OTHER')),
    description      TEXT NOT NULL,
    source_ref       VARCHAR(300),
    received_on      DATE NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'RECEIVED' CHECK (status IN ('RECEIVED', 'WITHDRAWN')),
    withdraw_reason  TEXT,
    withdrawn_by     UUID,
    review           VARCHAR(20) CHECK (review IN ('ACCEPTED', 'QUERIED')),
    review_note      TEXT,
    reviewed_on      DATE,
    reviewed_by      UUID,
    added_by         UUID NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    version          BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_trainprov_rpl_ev_desc CHECK (COALESCE(char_length(btrim(description)), 0) >= 10),
    CONSTRAINT ck_trainprov_rpl_ev_withdraw CHECK (
        (status = 'RECEIVED' AND withdraw_reason IS NULL AND withdrawn_by IS NULL)
        OR (status = 'WITHDRAWN' AND COALESCE(char_length(btrim(withdraw_reason)), 0) >= 10 AND withdrawn_by IS NOT NULL)),
    CONSTRAINT ck_trainprov_rpl_ev_review CHECK (
        (review IS NULL AND review_note IS NULL AND reviewed_on IS NULL AND reviewed_by IS NULL)
        OR (review IS NOT NULL AND COALESCE(char_length(btrim(review_note)), 0) >= 10 AND reviewed_on IS NOT NULL AND reviewed_by IS NOT NULL AND reviewed_on >= received_on))
);
CREATE INDEX idx_trainprov_rpl_ev_app ON trainprov_rpl_evidence (tenant_id, application_id);
