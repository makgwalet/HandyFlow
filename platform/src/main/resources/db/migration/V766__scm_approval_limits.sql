-- V766: monetary approval limits.
-- A limit says how much one person (or everyone holding a role) may approve for one kind of document.
-- max_amount NULL means unlimited. Resolution (ApprovalLimitRules): a person's own limit for the document type, else their own "*"
-- limit, else the most generous of their roles' limits (each role's own type-specific limit beating its "*"). No limit at all means
-- no authority once the tenant turns enforcement on. Enforcement is OFF until a company switches it on, so nothing changes on deploy.

CREATE TABLE IF NOT EXISTS sc_approval_limits (
    id              UUID PRIMARY KEY,
    tenant_id       UUID          NOT NULL,
    subject_type    VARCHAR(4)    NOT NULL,
    subject_id      UUID          NOT NULL,
    document_type   VARCHAR(20)   NOT NULL DEFAULT '*',
    max_amount      NUMERIC(15,2),
    created_by      UUID,
    created_by_name VARCHAR(255),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_sc_limit_subject CHECK (subject_type IN ('USER','ROLE')),
    CONSTRAINT chk_sc_limit_doc CHECK (document_type IN ('*','REQUISITION','PURCHASE_ORDER','RFQ_AWARD','PO_AMENDMENT','SUPPLIER_INVOICE')),
    CONSTRAINT chk_sc_limit_amount CHECK (max_amount IS NULL OR max_amount >= 0),
    CONSTRAINT uq_sc_limit UNIQUE (tenant_id, subject_type, subject_id, document_type)
);
CREATE INDEX IF NOT EXISTS idx_sc_limits_tenant ON sc_approval_limits (tenant_id);

CREATE TABLE IF NOT EXISTS sc_approval_limit_policy (
    tenant_id       UUID PRIMARY KEY,
    mode            VARCHAR(10)  NOT NULL DEFAULT 'OFF',
    updated_by      UUID,
    updated_by_name VARCHAR(255),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_sc_limit_policy_mode CHECK (mode IN ('OFF','ENFORCE'))
);

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'SCM_APPROVAL_LIMIT_MANAGE', 'Set the amounts people and roles may approve, and switch limit enforcement on or off')
ON CONFLICT (name) DO NOTHING;

UPDATE permissions SET module_key = 'supply_chain', resource = 'approval_limit', verb = 'MANAGE',
       sensitivity = 'FINANCIAL', requires_second_person = TRUE, grantable_by = 'ADMIN'
 WHERE name = 'SCM_APPROVAL_LIMIT_MANAGE';

INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, np.id
  FROM role_permissions rp
  JOIN permissions admin_p ON admin_p.id = rp.permission_id AND admin_p.name = 'SCM_ADMIN'
  JOIN permissions np ON np.name = 'SCM_APPROVAL_LIMIT_MANAGE'
 WHERE NOT EXISTS (SELECT 1 FROM role_permissions x WHERE x.role_id = rp.role_id AND x.permission_id = np.id);
