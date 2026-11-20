-- ============================================================================
-- Legal / Compliance — committee resolution voting. Depends on V813 (confirm the next free Flyway version before applying).
--
-- A ballot is the voting record for one DECISION: its type (ordinary: more than half of the votes for or against; special: at least 75%), whether voting is still
-- open, and, once closed, the outcome and the tally as counted. Votes are one row per member per decision. Once a ballot is CLOSED nothing about it changes.
-- ============================================================================

CREATE TABLE legalcompliance_resolution_ballots (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL,
    resolution_id      UUID NOT NULL REFERENCES legalcompliance_committee_resolutions (id),
    meeting_id         UUID NOT NULL,
    committee_id       UUID NOT NULL,
    resolution_type    VARCHAR(10) NOT NULL DEFAULT 'ORDINARY',
    status             VARCHAR(6) NOT NULL DEFAULT 'OPEN',
    outcome            VARCHAR(11),
    votes_for          INTEGER,
    votes_against      INTEGER,
    abstained          INTEGER,
    recused            INTEGER,
    closed_on          DATE,
    closed_by_name     VARCHAR(255),
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lc_ballot_resolution UNIQUE (resolution_id),
    CONSTRAINT ck_lc_ballot_type CHECK (resolution_type IN ('ORDINARY','SPECIAL')),
    CONSTRAINT ck_lc_ballot_status CHECK (status IN ('OPEN','CLOSED')),
    CONSTRAINT ck_lc_ballot_outcome CHECK (outcome IS NULL OR outcome IN ('CARRIED','NOT_CARRIED')),
    CONSTRAINT ck_lc_ballot_closed CHECK ((status = 'OPEN' AND outcome IS NULL) OR (status = 'CLOSED' AND outcome IS NOT NULL AND closed_on IS NOT NULL))
);
CREATE INDEX idx_lc_ballot_meeting ON legalcompliance_resolution_ballots (tenant_id, meeting_id);

CREATE TABLE legalcompliance_resolution_votes (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL,
    resolution_id      UUID NOT NULL REFERENCES legalcompliance_committee_resolutions (id),
    member_id          UUID NOT NULL,
    member_name        VARCHAR(255) NOT NULL,
    choice             VARCHAR(7) NOT NULL,
    reason             VARCHAR(500),
    recorded_by_name   VARCHAR(255),
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lc_vote_member UNIQUE (resolution_id, member_id),
    CONSTRAINT ck_lc_vote_choice CHECK (choice IN ('FOR','AGAINST','ABSTAIN','RECUSED')),
    CONSTRAINT ck_lc_vote_recused CHECK (choice <> 'RECUSED' OR (reason IS NOT NULL AND length(trim(reason)) >= 10))
);
CREATE INDEX idx_lc_vote_resolution ON legalcompliance_resolution_votes (tenant_id, resolution_id);
