-- V269__seed_missing_permissions_test_debt_sweep.sql
-- Confirmed via PermissionConsistencyTest: 32 permission strings were
-- checked in real @PreAuthorize annotations across the codebase but never
-- seeded into the `permissions` table — the same class of bug V220 already
-- fixed once for PAYROLLBUREAU_*/VIP_DETAIL_ACCESS (working login, correct-
-- looking JWT, 403 on every real call, nothing at build/startup time to
-- catch it). Same fix shape here: 30 of the 32 genuinely need a row in this
-- table; the other 2 (SUPERADMIN, PORTAL_USER) do NOT — see the note at the
-- bottom of this file for why those two are deliberately excluded.
--
-- Every module below is an ordinary business module, same category
-- PAYROLLBUREAU_* was — auto-granting to every tenant's ADMIN role
-- unconditionally is the established, correct default (V220's own
-- precedent), not gated behind a tenant_modules active/trial check the way
-- V220's bespoke VIP_DETAIL_ACCESS was (that one gets the stricter
-- treatment specifically because it's an extra-sensitive sub-permission on
-- top of an already-installed module, not a module's own baseline access
-- tier).

-- ── Accountant — AccDocumentRequestController's own two-tier set
-- (READ/WRITE, not this codebase's usual READ/MANAGE/ADMIN — matches
-- exactly what that controller actually checks, not invented here).
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'ACCOUNTANT_READ',  'View accountant practice client document requests'),
    (gen_random_uuid(), 'ACCOUNTANT_WRITE', 'Update accountant practice client document requests')
ON CONFLICT (name) DO NOTHING;

-- ── Accounting — the core double-entry ledger module (distinct from
-- `accountant`, the outsourced-bookkeeping-practice module above; despite
-- the near-identical name these are two separate modules in this
-- codebase). Only MANAGE/ADMIN found referenced — no READ literal turned
-- up in AccountingController, so none is seeded here; not this
-- migration's place to invent a permission no controller actually checks.
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'ACCOUNTING_MANAGE', 'Reconcile transactions and manage the general ledger'),
    (gen_random_uuid(), 'ACCOUNTING_ADMIN',  'Close VAT periods and other period-locking accounting actions')
ON CONFLICT (name) DO NOTHING;

-- ── Contracting
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'CONTRACTS_MANAGE', 'Manage contracts and contract parties'),
    (gen_random_uuid(), 'CONTRACTS_ADMIN',  'Terminate contracts and other admin-level contract actions')
ON CONFLICT (name) DO NOTHING;

-- ── Events
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'EVENTS_READ',   'View events'),
    (gen_random_uuid(), 'EVENTS_MANAGE', 'Manage events and check in attendees')
ON CONFLICT (name) DO NOTHING;

-- ── Fleet
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'FLEET_READ',   'View fleet and driver records'),
    (gen_random_uuid(), 'FLEET_MANAGE', 'Manage drivers and fleet records'),
    (gen_random_uuid(), 'FLEET_ADMIN',  'Delete drivers and other admin-level fleet actions')
ON CONFLICT (name) DO NOTHING;

-- ── Fuel
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'FUEL_READ',   'View fuel tank and delivery records'),
    (gen_random_uuid(), 'FUEL_MANAGE', 'Manage fuel deliveries')
ON CONFLICT (name) DO NOTHING;

-- ── HR — module-baseline READ/MANAGE plus its own payroll-processing
-- sub-tier (PAYROLL_READ/PAYROLL_RUN, both referenced from
-- HrController#getPayRuns — this HR module's own internal payroll
-- processing, distinct from the separate payrollbureau outsourced-
-- provider module and its own already-seeded PAYROLLBUREAU_* set).
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'HR_READ',      'View employees and leave requests'),
    (gen_random_uuid(), 'HR_MANAGE',    'Create and manage employees'),
    (gen_random_uuid(), 'PAYROLL_READ', 'View pay runs'),
    (gen_random_uuid(), 'PAYROLL_RUN',  'Process pay runs')
ON CONFLICT (name) DO NOTHING;

-- ── Insurance (policy administration)
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'INSURANCE_READ',   'View insurance policies'),
    (gen_random_uuid(), 'INSURANCE_MANAGE', 'Manage insurance policies'),
    (gen_random_uuid(), 'INSURANCE_ADMIN',  'Mark policies lapsed and other admin-level policy actions')
ON CONFLICT (name) DO NOTHING;

-- ── Insurance Brokerage — the outsourced-provider brokerage module
-- (distinct from `insurance` above). NOTE: this module's own migration,
-- V268__insurancebrokerage_module.sql, already contains this exact
-- 3-tier INSERT — but only as a commented-out block (lines 115-117,
-- `-- INSERT INTO permission (...) VALUES (...)`), apparently left as a
-- reminder and never actually uncommitted. Confirmed via direct
-- inspection this never ran. Seeding it for real here rather than
-- editing an already-applied historical migration.
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'INSURANCEBROKERAGE_READ',   'View insurance brokerage clients and policies'),
    (gen_random_uuid(), 'INSURANCEBROKERAGE_MANAGE', 'Manage insurance brokerage clients and policies'),
    (gen_random_uuid(), 'INSURANCEBROKERAGE_ADMIN',  'Full administrative access to insurance brokerage')
ON CONFLICT (name) DO NOTHING;

-- ── Property
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'PROPERTY_READ',   'View properties and inspections'),
    (gen_random_uuid(), 'PROPERTY_MANAGE', 'Create and manage properties'),
    (gen_random_uuid(), 'PROPERTY_ADMIN',  'Delete properties and other admin-level property actions')
ON CONFLICT (name) DO NOTHING;

-- ── Security (guarding/armoury) — READ/MANAGE/ADMIN plus its own
-- operational SECURITY_GUARD tier (CheckpointScanController#scan — a
-- working guard's own scan-in/scan-out action, deliberately a different
-- tier from the READ/MANAGE/ADMIN back-office set, matching exactly what
-- that controller checks rather than folding it into READ).
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'SECURITY_READ',   'View armoury and checkpoint records'),
    (gen_random_uuid(), 'SECURITY_MANAGE', 'Register armoury items and manage security records'),
    (gen_random_uuid(), 'SECURITY_ADMIN',  'Report lost items and other admin-level security actions'),
    (gen_random_uuid(), 'SECURITY_GUARD',  'Perform checkpoint scans as an on-duty guard')
ON CONFLICT (name) DO NOTHING;

-- ── Grant every permission seeded above to every tenant's existing ADMIN
-- role, matching V220's own precedent exactly.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name IN (
    'ACCOUNTANT_READ','ACCOUNTANT_WRITE',
    'ACCOUNTING_MANAGE','ACCOUNTING_ADMIN',
    'CONTRACTS_MANAGE','CONTRACTS_ADMIN',
    'EVENTS_READ','EVENTS_MANAGE',
    'FLEET_READ','FLEET_MANAGE','FLEET_ADMIN',
    'FUEL_READ','FUEL_MANAGE',
    'HR_READ','HR_MANAGE','PAYROLL_READ','PAYROLL_RUN',
    'INSURANCE_READ','INSURANCE_MANAGE','INSURANCE_ADMIN',
    'INSURANCEBROKERAGE_READ','INSURANCEBROKERAGE_MANAGE','INSURANCEBROKERAGE_ADMIN',
    'PROPERTY_READ','PROPERTY_MANAGE','PROPERTY_ADMIN',
    'SECURITY_READ','SECURITY_MANAGE','SECURITY_ADMIN','SECURITY_GUARD'
  )
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- ── Deliberately NOT seeded — SUPERADMIN and PORTAL_USER:
--
-- SUPERADMIN: AdminController checks @PreAuthorize("hasRole('SUPERADMIN')"),
-- not hasAuthority(...)/hasAnyAuthority(...). hasRole() checks for a
-- ROLE_-prefixed authority, and confirmed via V42__admin_portal.sql's own
-- comment ("their JWT carries ROLE_SUPERADMIN claim"), that authority is
-- granted directly by AdminJwtFilter parsing the separate platform-admin
-- JWT — a completely different authentication system from regular tenant
-- users, with no dependency on this table at all. PermissionConsistencyTest
-- flagged it because its extraction regex matches any quoted literal
-- inside @PreAuthorize, not specifically ones inside hasAuthority(...)/
-- hasAnyAuthority(...) — a real gap in that test, not in this schema.
--
-- PORTAL_USER: AccountantPortalAuthController checks
-- @PreAuthorize("hasAuthority('PORTAL_USER')") — a genuine hasAuthority()
-- call this time, but confirmed via PortalJwtFilter.java (grep for
-- SimpleGrantedAuthority) that it's granted directly by that filter as a
-- hardcoded synthetic marker ("this request came from a portal-user JWT"),
-- the same pattern as SUPERADMIN just via a different filter — not a
-- module-specific, per-tenant-assignable permission a real DB row would
-- represent.
--
-- Both are legitimate authorities, correctly enforced today, and correctly
-- absent from this table — flagging here so the next person auditing
-- PermissionConsistencyTest's own output doesn't re-flag them as bugs.
