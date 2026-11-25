-- Training provider: learner completion records. A completion is a person's recorded outcome for an enrolment (completed, or did not complete),
-- with a written basis. The system shows readiness from the competency decisions and moderation, but never completes anyone itself. Completing
-- with items still open needs a written reason and keeps a snapshot of what was open. Completion is not certification and does not change the
-- enrolment's own status. Records are withdrawn with a reason, never deleted.

CREATE TABLE trainprov_completions (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    completion_no         VARCHAR(30) NOT NULL,
    enrolment_id          UUID NOT NULL REFERENCES trainprov_learner_enrolments(id),
    outcome               VARCHAR(20) NOT NULL CHECK (outcome IN ('COMPLETED', 'DID_NOT_COMPLETE')),
    outcome_on            DATE NOT NULL,
    basis                 TEXT NOT NULL,
    open_gaps             INTEGER NOT NULL DEFAULT 0 CHECK (open_gaps >= 0),
    gaps_snapshot         TEXT,
    gaps_override_reason  TEXT,
    status                VARCHAR(20) NOT NULL DEFAULT 'RECORDED' CHECK (status IN ('RECORDED', 'WITHDRAWN')),
    withdrawn_reason      TEXT,
    withdrawn_by          UUID,
    withdrawn_at          TIMESTAMPTZ,
    recorded_by           UUID NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_trainprov_completion_no UNIQUE (tenant_id, completion_no),
    CONSTRAINT ck_trainprov_completion_basis CHECK (COALESCE(char_length(btrim(basis)), 0) >= 10),
    CONSTRAINT ck_trainprov_completion_gaps CHECK (
        outcome <> 'COMPLETED' OR open_gaps = 0 OR COALESCE(char_length(btrim(gaps_override_reason)), 0) >= 10),
    CONSTRAINT ck_trainprov_completion_override CHECK (gaps_override_reason IS NULL OR COALESCE(char_length(btrim(gaps_override_reason)), 0) >= 10),
    CONSTRAINT ck_trainprov_completion_withdrawn CHECK (
        (status = 'RECORDED' AND withdrawn_reason IS NULL AND withdrawn_by IS NULL AND withdrawn_at IS NULL)
        OR (status = 'WITHDRAWN' AND COALESCE(char_length(btrim(withdrawn_reason)), 0) >= 10 AND withdrawn_by IS NOT NULL AND withdrawn_at IS NOT NULL))
);
-- One live outcome per enrolment.
CREATE UNIQUE INDEX uq_trainprov_completion_live ON trainprov_completions (tenant_id, enrolment_id) WHERE status = 'RECORDED';
CREATE INDEX idx_trainprov_completion_enrolment ON trainprov_completions (tenant_id, enrolment_id);
