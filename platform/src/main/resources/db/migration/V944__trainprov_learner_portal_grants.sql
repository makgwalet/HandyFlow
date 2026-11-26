-- Training Provider 0178: learner portal access.
-- An administrator invites a learner by email, with a written reason. The learner accepts the invite and sets a password; the login is then linked to
-- that one learner. The link is ended with a written reason, never deleted. Read-only portal: this table only says who may look at whose own records.
-- At most one live (pending or accepted) link per learner.

CREATE TABLE trainprov_learner_portal_grants (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    learner_id        UUID NOT NULL REFERENCES trainprov_learners(id),
    invite_email      VARCHAR(255) NOT NULL,
    invite_token      VARCHAR(100) NOT NULL UNIQUE,
    invite_expires_at TIMESTAMPTZ NOT NULL,
    invite_reason     TEXT NOT NULL,
    invited_by        UUID NOT NULL,
    portal_user_id    UUID,
    accepted_at       TIMESTAMPTZ,
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ended_reason      TEXT,
    ended_by          UUID,
    ended_at          TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_trainprov_lpg_status CHECK (COALESCE(status IN ('PENDING', 'ACCEPTED', 'ENDED'), FALSE)),
    CONSTRAINT ck_trainprov_lpg_reason CHECK (COALESCE(char_length(btrim(invite_reason)), 0) >= 10),
    CONSTRAINT ck_trainprov_lpg_email CHECK (COALESCE(position('@' IN invite_email) > 1, FALSE)),
    CONSTRAINT ck_trainprov_lpg_state CHECK (
        (status = 'PENDING' AND portal_user_id IS NULL AND accepted_at IS NULL AND ended_reason IS NULL AND ended_by IS NULL AND ended_at IS NULL)
        OR (status = 'ACCEPTED' AND portal_user_id IS NOT NULL AND accepted_at IS NOT NULL AND ended_reason IS NULL AND ended_by IS NULL AND ended_at IS NULL)
        OR (status = 'ENDED' AND COALESCE(char_length(btrim(ended_reason)), 0) >= 10 AND ended_by IS NOT NULL AND ended_at IS NOT NULL
            AND ((portal_user_id IS NULL AND accepted_at IS NULL) OR (portal_user_id IS NOT NULL AND accepted_at IS NOT NULL))))
);

CREATE UNIQUE INDEX uq_trainprov_lpg_live ON trainprov_learner_portal_grants (learner_id) WHERE status IN ('PENDING', 'ACCEPTED');
CREATE INDEX ix_trainprov_lpg_user ON trainprov_learner_portal_grants (portal_user_id) WHERE status = 'ACCEPTED';
CREATE INDEX ix_trainprov_lpg_tenant ON trainprov_learner_portal_grants (tenant_id, learner_id);
