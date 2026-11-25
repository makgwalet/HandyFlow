-- Training provider: the provider's own learner documents. A completion letter or a participation statement, issued from the provider's own
-- records and carrying fixed wording that it is not a certificate of any authority. A letter is issued only from a standing 'completed' outcome.
-- Hand-over is recorded (when, to whom, how). A document is voided with a reason, never deleted. The older delegate certificates are separate.

CREATE TABLE trainprov_learner_documents (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    document_no         VARCHAR(30) NOT NULL,
    doc_type            VARCHAR(30) NOT NULL CHECK (doc_type IN ('COMPLETION_LETTER', 'PARTICIPATION_STATEMENT')),
    enrolment_id        UUID NOT NULL REFERENCES trainprov_learner_enrolments(id),
    learner_id          UUID NOT NULL REFERENCES trainprov_learners(id),
    completion_id       UUID REFERENCES trainprov_completions(id),
    learner_name        VARCHAR(300) NOT NULL,
    programme_title     VARCHAR(300) NOT NULL,
    version_no          INTEGER,
    issued_on           DATE NOT NULL,
    title               VARCHAR(200) NOT NULL,
    body                TEXT NOT NULL,
    disclaimer          TEXT NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'ISSUED' CHECK (status IN ('ISSUED', 'VOID')),
    void_reason         TEXT,
    void_by             UUID,
    void_at             TIMESTAMPTZ,
    handed_over_on      DATE,
    handed_over_to      VARCHAR(300),
    handed_over_method  VARCHAR(20) CHECK (handed_over_method IS NULL OR handed_over_method IN ('IN_PERSON', 'EMAIL', 'COLLECTED', 'POST')),
    handed_over_by      UUID,
    issued_by           UUID NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_trainprov_document_no UNIQUE (tenant_id, document_no),
    CONSTRAINT ck_trainprov_document_text CHECK (COALESCE(char_length(btrim(title)), 0) >= 2 AND COALESCE(char_length(btrim(body)), 0) >= 20),
    CONSTRAINT ck_trainprov_document_disclaimer CHECK (COALESCE(char_length(btrim(disclaimer)), 0) >= 40),
    CONSTRAINT ck_trainprov_document_completion CHECK (doc_type <> 'COMPLETION_LETTER' OR completion_id IS NOT NULL),
    CONSTRAINT ck_trainprov_document_void CHECK (
        (status = 'ISSUED' AND void_reason IS NULL AND void_by IS NULL AND void_at IS NULL)
        OR (status = 'VOID' AND COALESCE(char_length(btrim(void_reason)), 0) >= 10 AND void_by IS NOT NULL AND void_at IS NOT NULL)),
    CONSTRAINT ck_trainprov_document_handover CHECK (
        (handed_over_on IS NULL AND handed_over_to IS NULL AND handed_over_method IS NULL AND handed_over_by IS NULL)
        OR (handed_over_on IS NOT NULL AND COALESCE(char_length(btrim(handed_over_to)), 0) >= 2 AND handed_over_method IS NOT NULL AND handed_over_by IS NOT NULL))
);
-- One live document of each type per enrolment. To replace one, void it with a reason first.
CREATE UNIQUE INDEX uq_trainprov_document_live ON trainprov_learner_documents (tenant_id, enrolment_id, doc_type) WHERE status = 'ISSUED';
CREATE INDEX idx_trainprov_document_learner ON trainprov_learner_documents (tenant_id, learner_id);
