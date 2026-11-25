-- Training provider: moderation of assessment decisions. A moderator whose appointment was checked and in force on the day reviews a decided attempt
-- and either confirms it or queries it, with a written basis. One moderation per attempt. The decision itself is never edited: a query is answered
-- with a written resolution (for example that the attempt was voided and the learner reassessed).

CREATE TABLE trainprov_assessment_moderations (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    moderation_no   VARCHAR(30) NOT NULL,
    attempt_id      UUID NOT NULL UNIQUE REFERENCES trainprov_assessment_attempts(id),
    moderator_id    UUID NOT NULL REFERENCES trainprov_practitioners(id),
    appointment_id  UUID NOT NULL REFERENCES trainprov_practitioner_appointments(id),
    moderated_on    DATE NOT NULL,
    outcome         VARCHAR(20) NOT NULL CHECK (outcome IN ('CONFIRMED', 'QUERIED')),
    basis           TEXT NOT NULL,
    entered_by      UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolution_note TEXT,
    resolved_by     UUID,
    resolved_at     TIMESTAMPTZ,
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_trainprov_moderation_no UNIQUE (tenant_id, moderation_no),
    CONSTRAINT ck_trainprov_moderation_basis CHECK (COALESCE(char_length(btrim(basis)), 0) >= 10),
    CONSTRAINT ck_trainprov_moderation_resolution CHECK (
        (resolution_note IS NULL AND resolved_by IS NULL AND resolved_at IS NULL)
        OR (outcome = 'QUERIED' AND COALESCE(char_length(btrim(resolution_note)), 0) >= 10 AND resolved_by IS NOT NULL AND resolved_at IS NOT NULL))
);
CREATE INDEX idx_trainprov_moderation_tenant ON trainprov_assessment_moderations (tenant_id, outcome);
CREATE INDEX idx_trainprov_moderation_moderator ON trainprov_assessment_moderations (tenant_id, moderator_id);
