-- ============================================================================
-- Legal / Compliance — written (circular) resolutions: a committee decision taken without a meeting. Depends on V816 (confirm the next free Flyway version before applying).
--
-- A written resolution is circulated to the committee's voting members as they are on the day (active, not observers). Each answers FOR / AGAINST / ABSTAIN by the
-- deadline. It closes when everyone has answered or the deadline passes: UNANIMOUS needs every member FOR; MAJORITY_OF_ALL needs more than half of ALL members FOR
-- (silence and abstentions count against). Counts are fixed at closing. While OPEN, who has answered is visible but not how.
-- ============================================================================

CREATE TABLE legalcompliance_written_resolutions (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    committee_id        UUID NOT NULL REFERENCES legalcompliance_committees (id),
    reference           VARCHAR(20) NOT NULL,
    title               VARCHAR(255) NOT NULL,
    resolution_text     TEXT NOT NULL,
    threshold           VARCHAR(16) NOT NULL,
    deadline            DATE NOT NULL,
    status              VARCHAR(11) NOT NULL DEFAULT 'OPEN',
    circulated_on       DATE NOT NULL,
    member_count        INTEGER NOT NULL,
    closed_on           DATE,
    closed_automatically BOOLEAN NOT NULL DEFAULT FALSE,
    votes_for           INTEGER,
    votes_against       INTEGER,
    abstained           INTEGER,
    no_response         INTEGER,
    cancel_reason       TEXT,
    created_by_name     VARCHAR(255),
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lc_written_ref UNIQUE (tenant_id, reference),
    CONSTRAINT ck_lc_written_threshold CHECK (threshold IN ('UNANIMOUS','MAJORITY_OF_ALL')),
    CONSTRAINT ck_lc_written_status CHECK (status IN ('OPEN','CARRIED','NOT_CARRIED','CANCELLED')),
    CONSTRAINT ck_lc_written_members CHECK (member_count >= 1),
    CONSTRAINT ck_lc_written_closed CHECK (
        (status = 'OPEN' AND closed_on IS NULL)
        OR (status IN ('CARRIED','NOT_CARRIED') AND closed_on IS NOT NULL AND votes_for IS NOT NULL AND votes_against IS NOT NULL AND abstained IS NOT NULL AND no_response IS NOT NULL
            AND votes_for + votes_against + abstained + no_response = member_count)
        OR (status = 'CANCELLED' AND closed_on IS NOT NULL AND cancel_reason IS NOT NULL AND length(trim(cancel_reason)) >= 15))
);
CREATE INDEX idx_lc_written_committee ON legalcompliance_written_resolutions (tenant_id, committee_id, status);
CREATE INDEX idx_lc_written_open ON legalcompliance_written_resolutions (deadline) WHERE status = 'OPEN';

CREATE TABLE legalcompliance_written_responses (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    resolution_id       UUID NOT NULL REFERENCES legalcompliance_written_resolutions (id),
    member_id           UUID NOT NULL,
    member_name         VARCHAR(255) NOT NULL,
    user_id             UUID,
    choice              VARCHAR(7),
    reason              VARCHAR(500),
    responded_at        TIMESTAMPTZ,
    recorded_by_name    VARCHAR(255),
    how_received        VARCHAR(500),
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lc_written_response UNIQUE (resolution_id, member_id),
    CONSTRAINT ck_lc_written_choice CHECK (choice IS NULL OR choice IN ('FOR','AGAINST','ABSTAIN')),
    CONSTRAINT ck_lc_written_answered CHECK ((choice IS NULL AND responded_at IS NULL) OR (choice IS NOT NULL AND responded_at IS NOT NULL)),
    CONSTRAINT ck_lc_written_behalf CHECK (recorded_by_name IS NULL OR (how_received IS NOT NULL AND length(trim(how_received)) >= 10))
);
CREATE INDEX idx_lc_written_resp_res ON legalcompliance_written_responses (tenant_id, resolution_id);
CREATE INDEX idx_lc_written_resp_user ON legalcompliance_written_responses (tenant_id, user_id);
