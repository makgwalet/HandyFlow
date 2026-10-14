-- V407: external auditors only see what is explicitly shared, and every grant expires.
--
-- A1  Evidence and control exceptions reach an external auditor only after someone shares that item
--     (audit_shares). Nothing is shared by this migration.
-- A2  Grants get an expiry date and a scope note. Existing pending and active grants get 90 days from
--     now, so they keep working; the owner can change the date on each from the invite screen.
-- A3  One record of every share and withdrawal. Findings keep their own sharing columns for now.
-- A5  Real permissions for the evidence and control-exception endpoints (which only needed a login),
--     and a permission of its own for inviting auditors (which used SETTINGS_MANAGE).

ALTER TABLE auditor_access_grants
    ADD COLUMN expires_at TIMESTAMPTZ,
    ADD COLUMN scope_note TEXT;

UPDATE auditor_access_grants
SET expires_at = now() + INTERVAL '90 days'
WHERE status IN ('PENDING', 'ACTIVE');

CREATE TABLE audit_shares (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID         NOT NULL,
    item_type         VARCHAR(30)  NOT NULL CHECK (item_type IN ('EVIDENCE', 'CONTROL_EXCEPTION', 'FINDING')),
    item_id           UUID         NOT NULL,
    reason            TEXT         NOT NULL,
    shared_by         UUID         NOT NULL,
    shared_by_name    VARCHAR(255),
    shared_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    withdrawn_by      UUID,
    withdrawn_by_name VARCHAR(255),
    withdrawn_at      TIMESTAMPTZ,
    withdrawn_reason  TEXT
);
-- One live share per item; history of earlier shares and withdrawals stays.
CREATE UNIQUE INDEX uq_audit_shares_live ON audit_shares (tenant_id, item_type, item_id) WHERE withdrawn_at IS NULL;
CREATE INDEX idx_audit_shares_tenant ON audit_shares (tenant_id, item_type);

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'EXTERNAL_AUDIT_INVITE',        'Invite, re-date and revoke external auditors'),
    (gen_random_uuid(), 'EVIDENCE_READ',                'List and download attached evidence'),
    (gen_random_uuid(), 'EVIDENCE_WRITE',               'Attach and detach evidence'),
    (gen_random_uuid(), 'CONTROL_EXCEPTION_READ',       'See the control exceptions board'),
    (gen_random_uuid(), 'CONTROL_EXCEPTION_RESOLVE',    'Resolve a control exception')
ON CONFLICT (name) DO NOTHING;

-- Whoever could invite auditors keeps that ability.
INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, np.id
FROM role_permissions rp
JOIN permissions op ON op.id = rp.permission_id AND op.name = 'SETTINGS_MANAGE'
JOIN permissions np ON np.name = 'EXTERNAL_AUDIT_INVITE'
WHERE NOT EXISTS (SELECT 1 FROM role_permissions x WHERE x.role_id = rp.role_id AND x.permission_id = np.id);

-- Evidence and the exceptions board were open to every logged-in user. Every existing role keeps that
-- access for now, so nothing stops working; an owner can now take it away from a role.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE p.name IN ('EVIDENCE_READ', 'EVIDENCE_WRITE', 'CONTROL_EXCEPTION_READ', 'CONTROL_EXCEPTION_RESOLVE')
  AND NOT EXISTS (SELECT 1 FROM role_permissions x WHERE x.role_id = r.id AND x.permission_id = p.id);
