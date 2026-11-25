-- Training provider: assessment attempts and competency decisions.
-- An attempt records that a learner in a cohort sat an approved instrument on a day, with an assessor whose appointment was checked and in force
-- that day, and the mark (if the instrument is marked). The competency decision is a separate record, written once, with a stated basis.
-- No pass mark is stored anywhere and attendance plays no part: the decision is a person's.

CREATE TABLE trainprov_assessment_attempts (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    attempt_no     VARCHAR(30) NOT NULL,
    instrument_id  UUID NOT NULL REFERENCES trainprov_assessment_instruments(id),
    member_id      UUID NOT NULL REFERENCES trainprov_cohort_members(id),
    cohort_id      UUID NOT NULL REFERENCES trainprov_cohorts(id),
    enrolment_id   UUID NOT NULL REFERENCES trainprov_learner_enrolments(id),
    attempt_seq    INTEGER NOT NULL CHECK (attempt_seq >= 1),
    assessed_on    DATE NOT NULL,
    assessor_id    UUID NOT NULL REFERENCES trainprov_practitioners(id),
    appointment_id UUID NOT NULL REFERENCES trainprov_practitioner_appointments(id),
    mark           NUMERIC(8, 2),
    notes          TEXT,
    status         VARCHAR(20) NOT NULL DEFAULT 'RECORDED' CHECK (status IN ('RECORDED', 'VOID')),
    void_reason    TEXT,
    void_by        UUID,
    void_at        TIMESTAMPTZ,
    created_by     UUID NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    version        BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_trainprov_attempt_no UNIQUE (tenant_id, attempt_no),
    CONSTRAINT uq_trainprov_attempt_seq UNIQUE (instrument_id, member_id, attempt_seq),
    CONSTRAINT ck_trainprov_attempt_mark CHECK (mark IS NULL OR mark >= 0),
    CONSTRAINT ck_trainprov_attempt_void CHECK (
        (status = 'RECORDED' AND void_reason IS NULL AND void_by IS NULL AND void_at IS NULL)
        OR (status = 'VOID' AND COALESCE(char_length(btrim(void_reason)), 0) >= 10 AND void_by IS NOT NULL AND void_at IS NOT NULL))
);
CREATE INDEX idx_trainprov_attempt_member ON trainprov_assessment_attempts (tenant_id, member_id);
CREATE INDEX idx_trainprov_attempt_instrument ON trainprov_assessment_attempts (tenant_id, instrument_id);
CREATE INDEX idx_trainprov_attempt_enrolment ON trainprov_assessment_attempts (tenant_id, enrolment_id);

-- The decision. One per attempt, written once. A different view later is a new attempt or a moderation record, never an edit.
CREATE TABLE trainprov_assessment_decisions (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    attempt_id  UUID NOT NULL UNIQUE REFERENCES trainprov_assessment_attempts(id),
    decision    VARCHAR(30) NOT NULL CHECK (decision IN ('COMPETENT', 'NOT_YET_COMPETENT')),
    basis       TEXT NOT NULL,
    decided_by  UUID NOT NULL,
    decided_on  DATE NOT NULL,
    decided_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_trainprov_decision_basis CHECK (COALESCE(char_length(btrim(basis)), 0) >= 10)
);
CREATE INDEX idx_trainprov_decision_tenant ON trainprov_assessment_decisions (tenant_id, decision);

CREATE TABLE trainprov_assessment_attempt_events (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    attempt_id  UUID NOT NULL REFERENCES trainprov_assessment_attempts(id),
    action      VARCHAR(20) NOT NULL CHECK (action IN ('RECORDED', 'MARK_CORRECTED', 'DECIDED', 'VOIDED')),
    note        TEXT,
    acted_by    UUID NOT NULL,
    acted_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_trainprov_attempt_event ON trainprov_assessment_attempt_events (tenant_id, attempt_id, acted_at);
