-- G01: narrower Fuel write permissions that sit alongside FUEL_MANAGE.
-- FUEL_MANAGE still works everywhere it did, so existing roles keep exactly the access they had.
-- Each new permission opens only one area, so a tenant can now grant, say, price changes without granting the rest.
-- Only ADMIN is given the new permissions here; other roles keep FUEL_MANAGE and nothing changes for them.
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'FUEL_OPERATIONS_MANAGE', 'Manage fuel tanks, receipts, dispatches, deliveries and suppliers, without the rest of Fuel management'),
    (gen_random_uuid(), 'FUEL_STOCK_MANAGE', 'Manage fuel stock transfers and reservations (adjustments keep their own permissions)'),
    (gen_random_uuid(), 'FUEL_ORDERS_MANAGE', 'Manage fuel sales orders, loading records and invoicing from deliveries'),
    (gen_random_uuid(), 'FUEL_PURCHASING_MANAGE', 'Manage fuel purchase orders and receipts against them'),
    (gen_random_uuid(), 'FUEL_CUSTOMERS_MANAGE', 'Manage fuel customer accounts, sites and credit terms'),
    (gen_random_uuid(), 'FUEL_COMPLIANCE_MANAGE', 'Manage fuel compliance items, licence linkage and evidence'),
    (gen_random_uuid(), 'FUEL_TRANSPORT_MANAGE', 'Manage fuel tankers, drivers, planning and loads'),
    (gen_random_uuid(), 'FUEL_FORECOURT_MANAGE', 'Set up forecourt pumps and nozzles and reverse forecourt shifts'),
    (gen_random_uuid(), 'FUEL_SUPPLIER_INVOICE_MANAGE', 'Capture, match and decide fuel supplier invoices'),
    (gen_random_uuid(), 'FUEL_PRICING_MANAGE', 'Add and end fuel prices and contract prices'),
    (gen_random_uuid(), 'FUEL_PORTAL_MANAGE', 'Invite and remove customer portal users for fuel'),
    (gen_random_uuid(), 'FUEL_DISTRIBUTION_MANAGE', 'Manage delivery evidence and load variances')
ON CONFLICT (name) DO NOTHING;

UPDATE permissions SET module_key = 'fuel', resource = 'operations', verb = 'MANAGE', sensitivity = 'NORMAL', requires_second_person = false, grantable_by = 'ADMIN'
 WHERE name = 'FUEL_OPERATIONS_MANAGE';
UPDATE permissions SET module_key = 'fuel', resource = 'stock', verb = 'MANAGE', sensitivity = 'SENSITIVE', requires_second_person = false, grantable_by = 'ADMIN'
 WHERE name = 'FUEL_STOCK_MANAGE';
UPDATE permissions SET module_key = 'fuel', resource = 'order', verb = 'MANAGE', sensitivity = 'FINANCIAL', requires_second_person = false, grantable_by = 'ADMIN'
 WHERE name = 'FUEL_ORDERS_MANAGE';
UPDATE permissions SET module_key = 'fuel', resource = 'purchasing', verb = 'MANAGE', sensitivity = 'FINANCIAL', requires_second_person = false, grantable_by = 'ADMIN'
 WHERE name = 'FUEL_PURCHASING_MANAGE';
UPDATE permissions SET module_key = 'fuel', resource = 'customer', verb = 'MANAGE', sensitivity = 'NORMAL', requires_second_person = false, grantable_by = 'ADMIN'
 WHERE name = 'FUEL_CUSTOMERS_MANAGE';
UPDATE permissions SET module_key = 'fuel', resource = 'compliance', verb = 'MANAGE', sensitivity = 'SENSITIVE', requires_second_person = false, grantable_by = 'ADMIN'
 WHERE name = 'FUEL_COMPLIANCE_MANAGE';
UPDATE permissions SET module_key = 'fuel', resource = 'transport', verb = 'MANAGE', sensitivity = 'NORMAL', requires_second_person = false, grantable_by = 'ADMIN'
 WHERE name = 'FUEL_TRANSPORT_MANAGE';
UPDATE permissions SET module_key = 'fuel', resource = 'forecourt', verb = 'MANAGE', sensitivity = 'NORMAL', requires_second_person = false, grantable_by = 'ADMIN'
 WHERE name = 'FUEL_FORECOURT_MANAGE';
UPDATE permissions SET module_key = 'fuel', resource = 'supplier_invoice', verb = 'MANAGE', sensitivity = 'FINANCIAL', requires_second_person = false, grantable_by = 'ADMIN'
 WHERE name = 'FUEL_SUPPLIER_INVOICE_MANAGE';
UPDATE permissions SET module_key = 'fuel', resource = 'price', verb = 'MANAGE', sensitivity = 'FINANCIAL', requires_second_person = false, grantable_by = 'ADMIN'
 WHERE name = 'FUEL_PRICING_MANAGE';
UPDATE permissions SET module_key = 'fuel', resource = 'portal', verb = 'MANAGE', sensitivity = 'SENSITIVE', requires_second_person = false, grantable_by = 'ADMIN'
 WHERE name = 'FUEL_PORTAL_MANAGE';
UPDATE permissions SET module_key = 'fuel', resource = 'distribution', verb = 'MANAGE', sensitivity = 'NORMAL', requires_second_person = false, grantable_by = 'ADMIN'
 WHERE name = 'FUEL_DISTRIBUTION_MANAGE';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ADMIN' AND p.name IN ('FUEL_OPERATIONS_MANAGE','FUEL_STOCK_MANAGE','FUEL_ORDERS_MANAGE','FUEL_PURCHASING_MANAGE','FUEL_CUSTOMERS_MANAGE','FUEL_COMPLIANCE_MANAGE','FUEL_TRANSPORT_MANAGE','FUEL_FORECOURT_MANAGE','FUEL_SUPPLIER_INVOICE_MANAGE','FUEL_PRICING_MANAGE','FUEL_PORTAL_MANAGE','FUEL_DISTRIBUTION_MANAGE')
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
