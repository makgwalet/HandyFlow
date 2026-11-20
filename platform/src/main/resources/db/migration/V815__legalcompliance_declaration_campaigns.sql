-- ============================================================================
-- Legal / Compliance — staff-wide declaration campaigns. Depends on V808 (confirm the next free Flyway version before applying).
--
-- A campaign asks every active user (optionally one department) to answer: "I have nothing to declare", "my earlier declarations are unchanged", or to declare
-- interests (which creates ordinary conflict-of-interest declarations, reviewed as usual). One return per person per campaign. The campaign and its returns hold only
-- who answered what; the content of any declaration stays in the conflict-of-interest register.
-- ============================================================================

CREATE TABLE legalcompliance_declaration_campaigns (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL,
    reference          VARCHAR(20) NOT NULL,
    title              VARCHAR(255) NOT NULL,
    instructions       TEXT,
    due_date           DATE NOT NULL,
    department_filter  VARCHAR(100),
    status             VARCHAR(6) NOT NULL DEFAULT 'OPEN',
    launched_by        UUID,
    launched_by_name   VARCHAR(255),
    closed_at          TIMESTAMPTZ,
    closed_by_name     VARCHAR(255),
    close_reason       TEXT,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lc_deccamp_ref UNIQUE (tenant_id, reference),
    CONSTRAINT ck_lc_deccamp_status CHECK (status IN ('OPEN','CLOSED'))
);
CREATE INDEX idx_lc_deccamp_tenant ON legalcompliance_declaration_campaigns (tenant_id, status);
CREATE INDEX idx_lc_deccamp_open_due ON legalcompliance_declaration_campaigns (due_date) WHERE status = 'OPEN';

CREATE TABLE legalcompliance_declaration_returns (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL,
    campaign_id        UUID NOT NULL REFERENCES legalcompliance_declaration_campaigns (id),
    user_id            UUID NOT NULL,
    user_name          VARCHAR(255) NOT NULL,
    user_email         VARCHAR(255),
    status             VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    responded_at       TIMESTAMPTZ,
    statement          TEXT,
    declaration_ids    TEXT,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lc_decret_user UNIQUE (campaign_id, user_id),
    CONSTRAINT ck_lc_decret_status CHECK (status IN ('PENDING','NIL','UNCHANGED','DECLARED')),
    CONSTRAINT ck_lc_decret_answered CHECK (status = 'PENDING' OR responded_at IS NOT NULL)
);
CREATE INDEX idx_lc_decret_user ON legalcompliance_declaration_returns (tenant_id, user_id);
CREATE INDEX idx_lc_decret_campaign ON legalcompliance_declaration_returns (tenant_id, campaign_id);
