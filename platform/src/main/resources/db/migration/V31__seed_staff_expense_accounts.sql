-- V31__seed_staff_expense_accounts.sql
-- WHY? The expense claims module posts approved claims to accounting.
-- The ChartOfAccountsSeeder now includes these accounts for new tenants.
-- This migration backfills them for existing tenants who were already seeded
-- before these accounts were added.

-- Staff Expense Reimbursements account (5240) — the main posting account
-- for approved expense claims from the Expenses module
INSERT INTO acc_accounts (id, tenant_id, account_code, account_name,
                          account_type, account_subtype, is_system, active)
SELECT
    gen_random_uuid(),
    t.id,
    '5240',
    'Staff Expense Reimbursements',
    'EXPENSE',
    'STAFF_EXPENSES',
    false,
    true
FROM tenants t
WHERE t.id NOT IN (
    SELECT DISTINCT tenant_id FROM acc_accounts WHERE account_code = '5240'
)
AND t.id IN (
    -- Only tenants that already have a chart of accounts seeded
    SELECT DISTINCT tenant_id FROM acc_accounts
);

-- Travel and Subsistence (5241) — for TRAVEL category claims
INSERT INTO acc_accounts (id, tenant_id, account_code, account_name,
                          account_type, account_subtype, is_system, active)
SELECT
    gen_random_uuid(),
    t.id,
    '5241',
    'Travel and Subsistence',
    'EXPENSE',
    'STAFF_EXPENSES',
    false,
    true
FROM tenants t
WHERE t.id NOT IN (
    SELECT DISTINCT tenant_id FROM acc_accounts WHERE account_code = '5241'
)
AND t.id IN (
    SELECT DISTINCT tenant_id FROM acc_accounts
);

-- Meals and Entertainment (5242) — for MEALS and ENTERTAINMENT category claims
INSERT INTO acc_accounts (id, tenant_id, account_code, account_name,
                          account_type, account_subtype, is_system, active)
SELECT
    gen_random_uuid(),
    t.id,
    '5242',
    'Meals and Entertainment',
    'EXPENSE',
    'STAFF_EXPENSES',
    false,
    true
FROM tenants t
WHERE t.id NOT IN (
    SELECT DISTINCT tenant_id FROM acc_accounts WHERE account_code = '5242'
)
AND t.id IN (
    SELECT DISTINCT tenant_id FROM acc_accounts
);

-- Accommodation (5243) — for ACCOMMODATION category claims
INSERT INTO acc_accounts (id, tenant_id, account_code, account_name,
                          account_type, account_subtype, is_system, active)
SELECT
    gen_random_uuid(),
    t.id,
    '5243',
    'Accommodation',
    'EXPENSE',
    'STAFF_EXPENSES',
    false,
    true
FROM tenants t
WHERE t.id NOT IN (
    SELECT DISTINCT tenant_id FROM acc_accounts WHERE account_code = '5243'
)
AND t.id IN (
    SELECT DISTINCT tenant_id FROM acc_accounts
);
