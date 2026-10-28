-- The facilities and facilities-management controllers accept narrow authorities beside MANAGE and ADMIN (for example a role that may
-- only verify finished work orders), but these 24 were never created, so no role could be given them. PermissionConsistencyTest lists
-- them. Created here and granted to every tenant's ADMIN role, as the module's own permissions are; a tenant that wants a narrower role
-- assigns them by hand.
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'FACILITIES_AUDIT_READ',        'View the facilities audit log'),
    (gen_random_uuid(), 'FACILITIES_COMPLIANCE_MANAGE', 'Manage compliance certificates'),
    (gen_random_uuid(), 'FACILITIES_COMPLIANCE_REVOKE', 'Revoke compliance certificates'),
    (gen_random_uuid(), 'FACILITIES_INSPECT',           'Carry out and sync inspections'),
    (gen_random_uuid(), 'FACILITIES_PPM_MANAGE',        'Manage planned preventive maintenance schedules'),
    (gen_random_uuid(), 'FACILITIES_TECHNICIAN',        'Work on assigned jobs and record evidence'),
    (gen_random_uuid(), 'FACILITIES_VENDOR_APPROVE',    'Approve and suspend vendors'),
    (gen_random_uuid(), 'FACILITIES_WORKORDER_APPROVE', 'Approve work orders'),
    (gen_random_uuid(), 'FACILITIES_WORKORDER_ASSIGN',  'Assign work orders to technicians and vendors'),
    (gen_random_uuid(), 'FACILITIES_WORKORDER_CREATE',  'Create work orders'),
    (gen_random_uuid(), 'FACILITIES_WORKORDER_REQUEST', 'Raise work order requests'),
    (gen_random_uuid(), 'FACILITIES_WORKORDER_VERIFY',  'Verify completed work orders'),
    (gen_random_uuid(), 'FACILITIESMANAGEMENT_AUDIT_READ',     'View the facilities management audit log'),
    (gen_random_uuid(), 'FACILITIESMANAGEMENT_BILLING_APPROVE','Approve facilities management invoices and credit notes'),
    (gen_random_uuid(), 'FACILITIESMANAGEMENT_BILLING_MANAGE', 'Raise and cancel facilities management invoices'),
    (gen_random_uuid(), 'FACILITIESMANAGEMENT_CONTRACT_MANAGE','Manage service agreements and business calendars'),
    (gen_random_uuid(), 'FACILITIESMANAGEMENT_COST_READ',      'View costs and client profitability'),
    (gen_random_uuid(), 'FACILITIESMANAGEMENT_PORTAL_MANAGE',  'Manage client portal access'),
    (gen_random_uuid(), 'FACILITIESMANAGEMENT_PPM_MANAGE',     'Manage planned preventive maintenance schedules'),
    (gen_random_uuid(), 'FACILITIESMANAGEMENT_SLA_MANAGE',     'Manage service level agreements'),
    (gen_random_uuid(), 'FACILITIESMANAGEMENT_TECHNICIAN',     'Work on assigned jobs and record evidence'),
    (gen_random_uuid(), 'FACILITIESMANAGEMENT_VENDOR_APPROVE', 'Approve and suspend vendors'),
    (gen_random_uuid(), 'FACILITIESMANAGEMENT_WORKORDER_ASSIGN','Assign work orders to technicians and vendors'),
    (gen_random_uuid(), 'FACILITIESMANAGEMENT_WORKORDER_CREATE','Create work orders')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND (p.name LIKE 'FACILITIES\_%' ESCAPE '\' OR p.name LIKE 'FACILITIESMANAGEMENT\_%' ESCAPE '\')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
