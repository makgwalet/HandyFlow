-- ============================================================================
-- NUMBERING NOTE: same placeholder caution as V244/V245 before it. The
-- prior fix-session report flagged those two needed a real verification
-- pass against the actual Flyway schema history — that verification was
-- requested but never confirmed back before this task moved to
-- implementation. V246 is the best estimate given V245 was the last
-- confirmed-applied version, but has NOT been independently re-verified
-- against flyway_schema_history the way task 1 of this session asked
-- for. Confirm the real next version number before running.
-- ============================================================================

-- backlog 1.1 — shared Approval/Workflow Engine

CREATE TABLE approval_rules (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID,               -- nullable: NULL = platform default, applies to any tenant without an override
    module                VARCHAR(50)  NOT NULL,
    entity_type           VARCHAR(50)  NOT NULL,
    name                  VARCHAR(150) NOT NULL,
    active                BOOLEAN      NOT NULL DEFAULT true,
    priority              INT          NOT NULL DEFAULT 100,
    conditions            JSONB,
    approval_mode         VARCHAR(20)  NOT NULL,
    approver_chain        JSONB        NOT NULL,
    is_platform_default   BOOLEAN      NOT NULL DEFAULT false,
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_approval_rules_lookup ON approval_rules (module, entity_type, tenant_id, active);

CREATE TABLE approval_requests (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    module                VARCHAR(50) NOT NULL,
    entity_type           VARCHAR(50) NOT NULL,
    entity_id             UUID NOT NULL,
    rule_id               UUID,               -- nullable: an auto-approved request with no matched rule
    approval_mode         VARCHAR(20),         -- denormalized from the matched rule at submission time — see entity Javadoc
    status                VARCHAR(30) NOT NULL,
    submitted_by          UUID,
    submitted_at          TIMESTAMPTZ NOT NULL,
    completed_at          TIMESTAMPTZ,
    resubmitted_from_id   UUID,
    metadata              JSONB
);
CREATE INDEX idx_approval_requests_entity ON approval_requests (tenant_id, module, entity_type, entity_id);

CREATE TABLE approval_steps (
    id                              UUID PRIMARY KEY,
    approval_request_id             UUID NOT NULL,
    step_order                      INT NOT NULL,
    approver_type                   VARCHAR(30) NOT NULL,
    approver_value                  VARCHAR(255),
    approver_name                   VARCHAR(255),
    exclude_actor_of_previous_step  BOOLEAN NOT NULL DEFAULT false,
    status                          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    acted_by                        UUID,
    acted_at                        TIMESTAMPTZ,
    action_comment                  TEXT,
    actor_ip                        VARCHAR(64),
    public_token                    VARCHAR(255) UNIQUE,
    token_expires_at                TIMESTAMPTZ,
    created_at                      TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_approval_steps_request ON approval_steps (approval_request_id, step_order);

CREATE TABLE approval_delegations (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    delegator_user_id     UUID NOT NULL,
    delegate_user_id      UUID NOT NULL,
    start_date            DATE NOT NULL,
    end_date              DATE,
    scope_module          VARCHAR(50),
    reason                TEXT,
    active                BOOLEAN NOT NULL DEFAULT true,
    created_at            TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_approval_delegations_delegator ON approval_delegations (tenant_id, delegator_user_id, active);

-- Seed AP's platform-default rule — matches ap.ApService's own current
-- second-approval-threshold logic exactly (R10,000, two ROLE steps, the
-- second excluding whoever gave the first — the "different person" rule
-- AP currently hand-codes, now expressed as rule data instead). This
-- row is what makes "no tenant has configured their own AP rule yet"
-- mean "the existing R10k/two-step behaviour still applies," not "no
-- approval required at all" — the Q3 decision from the design addendum.
INSERT INTO approval_rules (id, tenant_id, module, entity_type, name, active, priority,
                            conditions, approval_mode, approver_chain, is_platform_default,
                            created_at, updated_at)
VALUES (
    gen_random_uuid(), NULL, 'ap', 'BILL', 'AP maker-checker (platform default)', true, 100,
    '{"totalAmount": {">=": 10000}}',
    'SEQUENTIAL',
    '[{"type":"ROLE","value":"AP_MANAGE"},{"type":"ROLE","value":"AP_MANAGE","excludeActorOfPreviousStep":true}]',
    true, now(), now()
);

-- New permission for tenant-facing rule management — same seeding
-- pattern as backlog 4.4's POPIA_EXPORT permission (INSERT ... ON
-- CONFLICT DO NOTHING, then grant to every tenant's ADMIN role).
INSERT INTO permissions (id, name, description)
VALUES (gen_random_uuid(), 'APPROVALS_MANAGE', 'Configure tenant approval rules across every module')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name = 'APPROVALS_MANAGE'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );