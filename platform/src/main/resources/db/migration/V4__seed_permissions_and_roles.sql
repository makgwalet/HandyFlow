-- src/main/resources/db/migration/V4__seed_permissions_and_roles.sql

-- =====================================================
-- SYSTEM PERMISSIONS
-- These are defined by US (the platform), not tenants.
-- Every tenant gets access to these permission definitions.
-- What differs per tenant is which ROLES have which permissions.
-- =====================================================

INSERT INTO permissions (id, name, description) VALUES
    -- User Management
    (gen_random_uuid(), 'USER_READ',       'View users in the tenant'),
    (gen_random_uuid(), 'USER_CREATE',     'Create new users'),
    (gen_random_uuid(), 'USER_UPDATE',     'Update existing users'),
    (gen_random_uuid(), 'USER_DELETE',     'Delete users'),

    -- Role Management
    (gen_random_uuid(), 'ROLE_READ',       'View roles and permissions'),
    (gen_random_uuid(), 'ROLE_MANAGE',     'Create and assign roles'),

    -- CRM
    (gen_random_uuid(), 'CUSTOMER_READ',   'View customers'),
    (gen_random_uuid(), 'CUSTOMER_CREATE', 'Create customers'),
    (gen_random_uuid(), 'CUSTOMER_UPDATE', 'Update customers'),
    (gen_random_uuid(), 'CUSTOMER_DELETE', 'Delete customers'),

    -- Invoicing
    (gen_random_uuid(), 'INVOICE_READ',    'View invoices'),
    (gen_random_uuid(), 'INVOICE_CREATE',  'Create invoices'),
    (gen_random_uuid(), 'INVOICE_SEND',    'Send invoices to customers'),
    (gen_random_uuid(), 'INVOICE_DELETE',  'Delete invoices'),

    -- Billing / Subscription
    (gen_random_uuid(), 'BILLING_READ',    'View subscription and billing info'),
    (gen_random_uuid(), 'BILLING_MANAGE',  'Manage subscription plans'),

    -- Reports
    (gen_random_uuid(), 'REPORT_VIEW',     'View reports and analytics'),

    -- System
    (gen_random_uuid(), 'SETTINGS_MANAGE', 'Manage company settings')

ON CONFLICT (name) DO NOTHING;
-- WHY ON CONFLICT DO NOTHING?
-- If this migration runs twice (e.g. in tests), it won't fail.
-- Permissions are identified by name — duplicates are harmless to skip.