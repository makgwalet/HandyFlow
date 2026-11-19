-- ============================================================================
-- Legal / Compliance — Phase 5: provider (consultancy) and client organisations. Depends on V293. Confirm the next free Flyway version before applying.
--
-- Each client organisation is its OWN tenant. A provider reaches a client only through a row in legalcompliance_provider_clients that
--   1. the provider creates (an invitation with a single-use code, stored only as a SHA-256 hash),
--   2. an administrator of the client tenant accepts, choosing which scopes to grant,
--   3. either side can suspend, and either side can end for good. A suspension made by the client can only be lifted by the client.
-- The row belongs to the provider tenant (tenant_id). client_tenant_id is only set once the client accepts, so a provider can never address a tenant it has not been invited by.
-- Nothing is deleted when access ends: the relationship, assignments and review history stay for the provider's records.
-- ============================================================================

CREATE TABLE legalcompliance_provider_clients (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    reference           VARCHAR(20) NOT NULL,
    client_name         VARCHAR(255) NOT NULL,
    provider_name       VARCHAR(255) NOT NULL,
    status              VARCHAR(10) NOT NULL DEFAULT 'INVITED',
    invite_hash         VARCHAR(64),
    invite_expires_at   TIMESTAMPTZ,
    requested_scopes    VARCHAR(30) NOT NULL,
    granted_scopes      VARCHAR(30) NOT NULL DEFAULT '',
    client_tenant_id    UUID,
    accepted_by_user_id UUID,
    accepted_by_name    VARCHAR(255),
    accepted_at         TIMESTAMPTZ,
    suspended_by        VARCHAR(10),
    status_reason       TEXT,
    contract_start      DATE,
    contract_end        DATE,
    review_cycle        VARCHAR(10) NOT NULL DEFAULT 'QUARTERLY',
    next_review_due     DATE,
    ended_at            TIMESTAMPTZ,
    ended_by            VARCHAR(10),
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lc_pc_ref UNIQUE (tenant_id, reference),
    CONSTRAINT ck_lc_pc_status CHECK (status IN ('INVITED','ACTIVE','SUSPENDED','ENDED')),
    CONSTRAINT ck_lc_pc_cycle CHECK (review_cycle IN ('MONTHLY','QUARTERLY','ANNUALLY')),
    CONSTRAINT ck_lc_pc_side CHECK ((suspended_by IS NULL OR suspended_by IN ('PROVIDER','CLIENT')) AND (ended_by IS NULL OR ended_by IN ('PROVIDER','CLIENT'))),
    CONSTRAINT ck_lc_pc_not_self CHECK (client_tenant_id IS NULL OR client_tenant_id <> tenant_id),
    CONSTRAINT ck_lc_pc_invited CHECK (status <> 'INVITED' OR (invite_hash IS NOT NULL AND invite_expires_at IS NOT NULL)),
    CONSTRAINT ck_lc_pc_linked CHECK (status NOT IN ('ACTIVE','SUSPENDED') OR (client_tenant_id IS NOT NULL AND granted_scopes <> '')),
    CONSTRAINT ck_lc_pc_suspended CHECK (status <> 'SUSPENDED' OR suspended_by IS NOT NULL),
    CONSTRAINT ck_lc_pc_dates CHECK (contract_start IS NULL OR contract_end IS NULL OR contract_end >= contract_start)
);
CREATE INDEX idx_lc_pc_tenant ON legalcompliance_provider_clients (tenant_id, status);
CREATE INDEX idx_lc_pc_client ON legalcompliance_provider_clients (client_tenant_id) WHERE client_tenant_id IS NOT NULL;
CREATE UNIQUE INDEX uq_lc_pc_invite ON legalcompliance_provider_clients (invite_hash) WHERE invite_hash IS NOT NULL;
-- A client organisation can be linked to a given provider only once at a time.
CREATE UNIQUE INDEX uq_lc_pc_link ON legalcompliance_provider_clients (tenant_id, client_tenant_id) WHERE status IN ('ACTIVE','SUSPENDED');
CREATE INDEX idx_lc_pc_review ON legalcompliance_provider_clients (next_review_due) WHERE status = 'ACTIVE';

CREATE TABLE legalcompliance_provider_assignments (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    provider_client_id  UUID NOT NULL REFERENCES legalcompliance_provider_clients (id) ON DELETE CASCADE,
    user_id             UUID NOT NULL,
    user_name           VARCHAR(255) NOT NULL,
    assignment_role     VARCHAR(10) NOT NULL,
    assigned_by_name    VARCHAR(255) NOT NULL,
    assigned_on         DATE NOT NULL,
    ended_on            DATE,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_lc_pa_role CHECK (assignment_role IN ('LEAD','REVIEWER','SUPPORT')),
    CONSTRAINT ck_lc_pa_dates CHECK (ended_on IS NULL OR ended_on >= assigned_on)
);
CREATE INDEX idx_lc_pa_client ON legalcompliance_provider_assignments (provider_client_id);
CREATE INDEX idx_lc_pa_user ON legalcompliance_provider_assignments (tenant_id, user_id) WHERE ended_on IS NULL;
CREATE UNIQUE INDEX uq_lc_pa_active_user ON legalcompliance_provider_assignments (provider_client_id, user_id) WHERE ended_on IS NULL;
CREATE UNIQUE INDEX uq_lc_pa_one_lead ON legalcompliance_provider_assignments (provider_client_id) WHERE assignment_role = 'LEAD' AND ended_on IS NULL;

CREATE TABLE legalcompliance_provider_reviews (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    provider_client_id  UUID NOT NULL REFERENCES legalcompliance_provider_clients (id) ON DELETE CASCADE,
    review_date         DATE NOT NULL,
    reviewer_user_id    UUID NOT NULL,
    reviewer_name       VARCHAR(255) NOT NULL,
    outcome             VARCHAR(16) NOT NULL,
    notes               TEXT NOT NULL,
    metrics             JSONB,
    next_review_due     DATE NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_lc_pr_outcome CHECK (outcome IN ('SATISFACTORY','ACTIONS_REQUIRED','ESCALATED'))
);
CREATE INDEX idx_lc_pr_client ON legalcompliance_provider_reviews (provider_client_id, review_date DESC);

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'LEGALCOMPLIANCE_PROVIDER_MANAGE', 'Manage client organisations, invitations, consultant assignments and reviews as a compliance service provider'),
    (gen_random_uuid(), 'LEGALCOMPLIANCE_PROVIDER_CONSULT', 'View the client organisations you are assigned to as a consultant')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name IN ('LEGALCOMPLIANCE_PROVIDER_MANAGE','LEGALCOMPLIANCE_PROVIDER_CONSULT')
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
