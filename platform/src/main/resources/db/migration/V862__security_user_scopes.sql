-- Per-user data scope for the Security module (SP-13). A user with no scope at all (no row here and no active USER row in
-- security_branch_assignments) sees the whole company, as before. A user with any scope sees only the sites in it:
--   branch  - from security_branch_assignments (entity_type = 'USER'), unchanged
--   client  - every site of that client (security_sites.customer_id)
--   site    - that one site
-- Scope is per user, not per role: roles grant capabilities, this table says where.
CREATE TABLE IF NOT EXISTS security_user_scopes (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id   UUID        NOT NULL REFERENCES tenants(id),
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    scope_type  VARCHAR(10) NOT NULL,
    scope_id    UUID        NOT NULL,
    created_by  UUID,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_security_user_scopes PRIMARY KEY (id),
    CONSTRAINT chk_security_user_scopes_type CHECK (scope_type IN ('CLIENT', 'SITE')),
    CONSTRAINT uq_security_user_scopes UNIQUE (tenant_id, user_id, scope_type, scope_id)
);
CREATE INDEX IF NOT EXISTS idx_security_user_scopes_user ON security_user_scopes (tenant_id, user_id);
COMMENT ON TABLE security_user_scopes IS 'Client and site scope of a staff user. No rows (and no branch assignment) = the whole company.';
