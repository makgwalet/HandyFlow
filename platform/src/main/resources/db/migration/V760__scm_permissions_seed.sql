-- V760: Supply Chain permissions that controllers already require but that were never seeded, plus the new ones.
--
-- Until now only SCM_ADMIN (seeded in V86) could reach endpoints guarded by SCM_SUPPLIER_*, SCM_BUDGET_*,
-- SCM_REQUISITION_*, SCM_PO_CREATE and SCM_AUDIT_VIEW, because those names did not exist in `permissions`, so they
-- could not be granted to anyone. Every controller accepts the fine-grained name OR SCM_ADMIN, so seeding them changes
-- nothing for existing admins; it lets a company grant the narrow permission without handing out SCM_ADMIN.
--
-- New: SCM_RECEIVE_REVERSE (reverse a posted goods receipt: stock and PO quantities change, so it is not a clerk's job),
-- and the permissions for contracts, returns and claims, quality inspection and RFQs.
-- Roles that already hold SCM_ADMIN receive every permission below, so nobody loses access.

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'SCM_AUDIT_VIEW',         'View the supplier audit trail'),
    (gen_random_uuid(), 'SCM_BUDGET_READ',        'View procurement budgets and commitments'),
    (gen_random_uuid(), 'SCM_BUDGET_MANAGE',      'Create and change procurement budgets'),
    (gen_random_uuid(), 'SCM_BUDGET_OVERRIDE',    'Approve a purchase order over a hard-stop budget'),
    (gen_random_uuid(), 'SCM_PO_CREATE',          'Turn an approved requisition into a purchase order'),
    (gen_random_uuid(), 'SCM_REQUISITION_CREATE', 'Raise and submit purchase requisitions'),
    (gen_random_uuid(), 'SCM_REQUISITION_APPROVE','Approve or reject purchase requisitions'),
    (gen_random_uuid(), 'SCM_SUPPLIER_MANAGE',    'Register and edit suppliers, documents and compliance'),
    (gen_random_uuid(), 'SCM_SUPPLIER_APPROVE',   'Approve suppliers and verified documents'),
    (gen_random_uuid(), 'SCM_SUPPLIER_BLOCK',     'Suspend, blacklist and reinstate suppliers'),
    (gen_random_uuid(), 'SCM_SUPPLIER_BANK_VERIFY','Verify supplier bank detail changes'),
    (gen_random_uuid(), 'SCM_RECEIVE_REVERSE',    'Reverse a posted goods receipt'),
    (gen_random_uuid(), 'SCM_CONTRACT_READ',      'View supplier contracts'),
    (gen_random_uuid(), 'SCM_CONTRACT_MANAGE',    'Create, change, activate and terminate supplier contracts'),
    (gen_random_uuid(), 'SCM_RETURN_MANAGE',      'Raise supplier returns and claims'),
    (gen_random_uuid(), 'SCM_RETURN_APPROVE',     'Approve and settle supplier returns and claims'),
    (gen_random_uuid(), 'SCM_QUALITY_MANAGE',     'Inspect received goods and record quality results'),
    (gen_random_uuid(), 'SCM_RFQ_MANAGE',         'Run RFQs: invite suppliers, compare quotations, award')
ON CONFLICT (name) DO NOTHING;

UPDATE permissions SET module_key = 'supply_chain', resource = lower(split_part(name, '_', 2)),
       verb = substr(name, length('SCM_' || split_part(name, '_', 2)) + 2),
       sensitivity = CASE WHEN name IN ('SCM_BUDGET_OVERRIDE','SCM_SUPPLIER_BANK_VERIFY','SCM_RECEIVE_REVERSE','SCM_RETURN_APPROVE')
                          THEN 'FINANCIAL' WHEN name IN ('SCM_SUPPLIER_BLOCK','SCM_CONTRACT_MANAGE','SCM_BUDGET_MANAGE') THEN 'SENSITIVE' ELSE 'NORMAL' END,
       requires_second_person = name IN ('SCM_BUDGET_OVERRIDE','SCM_SUPPLIER_BANK_VERIFY','SCM_RETURN_APPROVE','SCM_REQUISITION_APPROVE'),
       grantable_by = 'ADMIN'
 WHERE name IN ('SCM_AUDIT_VIEW','SCM_BUDGET_READ','SCM_BUDGET_MANAGE','SCM_BUDGET_OVERRIDE','SCM_PO_CREATE','SCM_REQUISITION_CREATE',
                'SCM_REQUISITION_APPROVE','SCM_SUPPLIER_MANAGE','SCM_SUPPLIER_APPROVE','SCM_SUPPLIER_BLOCK','SCM_SUPPLIER_BANK_VERIFY',
                'SCM_RECEIVE_REVERSE','SCM_CONTRACT_READ','SCM_CONTRACT_MANAGE','SCM_RETURN_MANAGE','SCM_RETURN_APPROVE',
                'SCM_QUALITY_MANAGE','SCM_RFQ_MANAGE');

INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, np.id
  FROM role_permissions rp
  JOIN permissions admin_p ON admin_p.id = rp.permission_id AND admin_p.name = 'SCM_ADMIN'
  JOIN permissions np ON np.name IN ('SCM_AUDIT_VIEW','SCM_BUDGET_READ','SCM_BUDGET_MANAGE','SCM_BUDGET_OVERRIDE','SCM_PO_CREATE',
        'SCM_REQUISITION_CREATE','SCM_REQUISITION_APPROVE','SCM_SUPPLIER_MANAGE','SCM_SUPPLIER_APPROVE','SCM_SUPPLIER_BLOCK',
        'SCM_SUPPLIER_BANK_VERIFY','SCM_RECEIVE_REVERSE','SCM_CONTRACT_READ','SCM_CONTRACT_MANAGE','SCM_RETURN_MANAGE',
        'SCM_RETURN_APPROVE','SCM_QUALITY_MANAGE','SCM_RFQ_MANAGE')
 WHERE NOT EXISTS (SELECT 1 FROM role_permissions x WHERE x.role_id = rp.role_id AND x.permission_id = np.id);
