-- V395 (finance patch F15, 2026-10-14): Accountant - client-level access and review/sign-off of a client's financial year.
--
-- Before this, anyone who could read accountant clients could read every client, and nothing recorded who prepared,
-- reviewed or signed off a client's year.
--   acc_client_assignments   who works on which client, as PREPARER, REVIEWER or PARTNER.
--   acc_engagements          one row per client and financial year (named by the calendar year it ends in), moving
--                            OPEN, PREPARED, REVIEWED, SIGNED_OFF, with who and when at each step.
--   acc_engagement_events    every step, including sending a year back or reopening it, with the reason.
-- Permissions:
--   ACCOUNTANT_ALL_CLIENTS   see every client. Granted to every role that holds ACCOUNTANT_READ, so nobody loses access on
--                            day one; take it off a role and its people see only clients they are assigned to.
--   ACCOUNTANT_ASSIGN        assign staff to clients (ADMIN and OWNER roles).
--   ACCOUNTANT_SIGNOFF       sign off a year and reopen a signed-off year (ADMIN and OWNER roles).
-- Re-runnable.

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'ACCOUNTANT_ALL_CLIENTS', 'See every client of the practice, not only the ones you are assigned to'),
    (gen_random_uuid(), 'ACCOUNTANT_ASSIGN',      'Assign staff to practice clients'),
    (gen_random_uuid(), 'ACCOUNTANT_SIGNOFF',     'Sign off a client''s financial year, and reopen a signed-off year')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.id
  FROM role_permissions rp
  JOIN permissions p ON p.id = rp.permission_id AND p.name = 'ACCOUNTANT_READ'
  JOIN permissions np ON np.name = 'ACCOUNTANT_ALL_CLIENTS'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT r.id, p.id FROM roles r JOIN permissions p ON p.name IN ('ACCOUNTANT_ASSIGN', 'ACCOUNTANT_SIGNOFF')
 WHERE r.name IN ('ADMIN', 'OWNER')
ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS acc_client_assignments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    client_id   UUID NOT NULL REFERENCES acc_clients(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL,
    role        VARCHAR(20) NOT NULL CHECK (role IN ('PREPARER', 'REVIEWER', 'PARTNER')),
    assigned_by UUID,
    assigned_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_acc_client_assignment UNIQUE (client_id, user_id, role)
);
CREATE INDEX IF NOT EXISTS idx_acc_client_assignments_user ON acc_client_assignments (tenant_id, user_id);

CREATE TABLE IF NOT EXISTS acc_engagements (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    client_id      UUID NOT NULL REFERENCES acc_clients(id) ON DELETE CASCADE,
    financial_year INT  NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'PREPARED', 'REVIEWED', 'SIGNED_OFF')),
    prepared_by    UUID,
    prepared_at    TIMESTAMP,
    reviewed_by    UUID,
    reviewed_at    TIMESTAMP,
    signed_by      UUID,
    signed_at      TIMESTAMP,
    note           VARCHAR(500),
    created_at     TIMESTAMP NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_acc_engagement UNIQUE (client_id, financial_year)
);
CREATE INDEX IF NOT EXISTS idx_acc_engagements_tenant ON acc_engagements (tenant_id, status);

CREATE TABLE IF NOT EXISTS acc_engagement_events (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    engagement_id UUID NOT NULL REFERENCES acc_engagements(id) ON DELETE CASCADE,
    action        VARCHAR(20) NOT NULL,
    by_user       UUID,
    note          VARCHAR(500),
    at            TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_acc_engagement_events ON acc_engagement_events (engagement_id, at);
