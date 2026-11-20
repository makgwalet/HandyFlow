-- ============================================================================
-- Legal / Compliance — committee proxies. Confirm the next free Flyway version before applying (V835 is chunk 32).
--
-- A proxy is made for ONE meeting: a voting member who cannot attend appoints a member who will be present to vote on their behalf, confirming the committee's charter allows it.
-- Proxies never count toward quorum (that stays physical presence). The holder's proxy votes are stored apart from members' own votes and are added to the tally when voting closes.
-- ============================================================================

CREATE TABLE legalcompliance_proxy_appointments (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    committee_id        UUID NOT NULL REFERENCES legalcompliance_committees (id),
    meeting_id          UUID NOT NULL REFERENCES legalcompliance_committee_meetings (id),
    grantor_member_id   UUID NOT NULL REFERENCES legalcompliance_committee_members (id),
    grantor_name        VARCHAR(255) NOT NULL,
    holder_member_id    UUID NOT NULL REFERENCES legalcompliance_committee_members (id),
    holder_name         VARCHAR(255) NOT NULL,
    charter_clause      VARCHAR(200) NOT NULL,
    instructions        VARCHAR(500),
    status              VARCHAR(7) NOT NULL DEFAULT 'ACTIVE',
    appointed_by_name   VARCHAR(255),
    revoked_reason      VARCHAR(500),
    revoked_by_name     VARCHAR(255),
    revoked_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_lc_proxy_status CHECK (status IN ('ACTIVE','REVOKED')),
    CONSTRAINT ck_lc_proxy_distinct CHECK (grantor_member_id <> holder_member_id),
    CONSTRAINT ck_lc_proxy_clause CHECK (length(trim(charter_clause)) >= 3),
    CONSTRAINT ck_lc_proxy_revoked CHECK ((status = 'ACTIVE' AND revoked_at IS NULL) OR (status = 'REVOKED' AND revoked_at IS NOT NULL AND revoked_reason IS NOT NULL AND length(trim(revoked_reason)) >= 10))
);
-- A member can have only one live proxy per meeting.
CREATE UNIQUE INDEX uq_lc_proxy_live ON legalcompliance_proxy_appointments (meeting_id, grantor_member_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_lc_proxy_meeting ON legalcompliance_proxy_appointments (tenant_id, meeting_id);

CREATE TABLE legalcompliance_proxy_votes (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    proxy_id            UUID NOT NULL REFERENCES legalcompliance_proxy_appointments (id),
    resolution_id       UUID NOT NULL REFERENCES legalcompliance_committee_resolutions (id),
    choice              VARCHAR(7) NOT NULL,
    reason              VARCHAR(500),
    recorded_by_name    VARCHAR(255),
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lc_proxy_vote UNIQUE (proxy_id, resolution_id),
    CONSTRAINT ck_lc_proxy_vote_choice CHECK (choice IN ('FOR','AGAINST','ABSTAIN'))
);
CREATE INDEX idx_lc_proxy_vote_resolution ON legalcompliance_proxy_votes (tenant_id, resolution_id);
-- No new permission: appointing and voting use the existing governance authorities.
