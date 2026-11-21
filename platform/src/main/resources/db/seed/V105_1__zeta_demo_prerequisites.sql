-- V105_1__zeta_demo_prerequisites.sql
--
-- WHY THIS EXISTS
-- V106, V107, V108, V119 and V120 are QA seed scripts for the "Zeta Earthmoving" tenant. They assume a tenant,
-- four guards, two sites and a chart of accounts that were created by hand (through the app) in the developer's
-- own database, so on any other database they fail and Flyway cannot build the schema from scratch. Those five
-- files are already applied in existing databases, so editing them would break their checksums.
--
-- WHAT THIS DOES
-- Creates exactly what those scripts expect, and only where it is missing. Every statement is guarded, so on a
-- database that already has the Zeta tenant (such as the developer's) it changes nothing. It carries a version
-- below 106 so a fresh database gets it first; an existing database applies it out of order, as a no-op.
--
-- FOLLOW-UP (not done here): move QA seed data out of db/migration, so that a production database never gets a
-- demo tenant. That needs a decision about checksums for databases that have already applied these files.

DO $$
DECLARE
    zeta UUID := '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f';
BEGIN
    INSERT INTO tenants (id, name, slug, email, status, tenant_id)
    SELECT zeta, 'Zeta Earthmoving (Pty) Ltd', 'zeta-earthmoving', 'admin@zeta-earthmoving.example', 'ACTIVE', zeta
     WHERE NOT EXISTS (SELECT 1 FROM tenants WHERE id = zeta)
       AND NOT EXISTS (SELECT 1 FROM tenants WHERE slug = 'zeta-earthmoving')
       AND NOT EXISTS (SELECT 1 FROM tenants WHERE email = 'admin@zeta-earthmoving.example');

    -- the same chart of accounts the application creates for a new tenant (ChartOfAccountsSeeder)
    IF EXISTS (SELECT 1 FROM tenants WHERE id = zeta) THEN
        INSERT INTO acc_accounts (tenant_id, account_code, account_name, account_type, account_subtype, is_system)
        SELECT zeta, v.code, v.name, v.type, v.subtype, true
          FROM (VALUES
        ('1000', 'Current Assets', 'ASSET', 'CURRENT_ASSETS'),
        ('1010', 'Cash and Cash Equivalents', 'ASSET', 'CASH'),
        ('1020', 'Bank — Cheque Account', 'ASSET', 'BANK'),
        ('1030', 'Bank — Savings Account', 'ASSET', 'BANK'),
        ('1100', 'Accounts Receivable', 'ASSET', 'RECEIVABLE'),
        ('1110', 'Trade Debtors', 'ASSET', 'RECEIVABLE'),
        ('1200', 'Inventory', 'ASSET', 'INVENTORY'),
        ('1300', 'VAT Input (Claimable)', 'ASSET', 'VAT'),
        ('1400', 'Prepaid Expenses', 'ASSET', 'PREPAID'),
        ('1500', 'Non-Current Assets', 'ASSET', 'NON_CURRENT'),
        ('1510', 'Property, Plant & Equipment', 'ASSET', 'FIXED_ASSET'),
        ('1520', 'Vehicles', 'ASSET', 'FIXED_ASSET'),
        ('1530', 'Equipment', 'ASSET', 'FIXED_ASSET'),
        ('1590', 'Accumulated Depreciation', 'ASSET', 'DEPRECIATION'),
        ('2000', 'Current Liabilities', 'LIABILITY', 'CURRENT'),
        ('2010', 'Accounts Payable', 'LIABILITY', 'PAYABLE'),
        ('2020', 'Trade Creditors', 'LIABILITY', 'PAYABLE'),
        ('2100', 'VAT Output (Payable)', 'LIABILITY', 'VAT'),
        ('2110', 'VAT Control Account', 'LIABILITY', 'VAT'),
        ('2200', 'PAYE Payable', 'LIABILITY', 'TAX'),
        ('2210', 'UIF Payable', 'LIABILITY', 'TAX'),
        ('2300', 'Short-term Loans', 'LIABILITY', 'LOAN'),
        ('2400', 'Accrued Expenses', 'LIABILITY', 'ACCRUAL'),
        ('2500', 'Non-Current Liabilities', 'LIABILITY', 'NON_CURRENT'),
        ('2510', 'Long-term Loans', 'LIABILITY', 'LOAN'),
        ('3000', 'Owner''s Equity', 'EQUITY', 'EQUITY'),
        ('3010', 'Share Capital', 'EQUITY', 'CAPITAL'),
        ('3020', 'Retained Earnings', 'EQUITY', 'RETAINED'),
        ('3030', 'Drawings', 'EQUITY', 'DRAWINGS'),
        ('3040', 'Current Year Earnings', 'EQUITY', 'CURRENT_EARNINGS'),
        ('4000', 'Revenue', 'INCOME', 'REVENUE'),
        ('4010', 'Sales — Products', 'INCOME', 'SALES'),
        ('4020', 'Sales — Services', 'INCOME', 'SALES'),
        ('4030', 'Hire Income', 'INCOME', 'HIRE'),
        ('4100', 'Other Income', 'INCOME', 'OTHER'),
        ('4110', 'Interest Received', 'INCOME', 'INTEREST'),
        ('4120', 'Discount Received', 'INCOME', 'DISCOUNT'),
        ('5000', 'Cost of Sales', 'EXPENSE', 'COS'),
        ('5010', 'Cost of Goods Sold', 'EXPENSE', 'COS'),
        ('5100', 'Operating Expenses', 'EXPENSE', 'OPERATING'),
        ('5110', 'Salaries and Wages', 'EXPENSE', 'PAYROLL'),
        ('5120', 'Rent Expense', 'EXPENSE', 'RENT'),
        ('5130', 'Utilities', 'EXPENSE', 'UTILITIES'),
        ('5140', 'Fuel and Travel', 'EXPENSE', 'TRAVEL'),
        ('5150', 'Vehicle Maintenance', 'EXPENSE', 'VEHICLE'),
        ('5160', 'Telephone and Internet', 'EXPENSE', 'COMMS'),
        ('5170', 'Office Supplies', 'EXPENSE', 'OFFICE'),
        ('5180', 'Marketing and Advertising', 'EXPENSE', 'MARKETING'),
        ('5190', 'Professional Fees', 'EXPENSE', 'PROFESSIONAL'),
        ('5200', 'Insurance', 'EXPENSE', 'INSURANCE'),
        ('5210', 'Bank Charges', 'EXPENSE', 'BANK_CHARGES'),
        ('5220', 'Depreciation', 'EXPENSE', 'DEPRECIATION'),
        ('5230', 'Bad Debts', 'EXPENSE', 'BAD_DEBTS'),
        ('5300', 'Finance Costs', 'EXPENSE', 'FINANCE'),
        ('5310', 'Interest Paid', 'EXPENSE', 'INTEREST'),
        ('5240', 'Staff Expense Reimbursements', 'EXPENSE', 'STAFF_EXPENSES'),
        ('5241', 'Travel and Subsistence', 'EXPENSE', 'STAFF_EXPENSES'),
        ('5242', 'Meals and Entertainment', 'EXPENSE', 'STAFF_EXPENSES'),
        ('5243', 'Accommodation', 'EXPENSE', 'STAFF_EXPENSES')
          ) AS v(code, name, type, subtype)
        ON CONFLICT (tenant_id, account_code) DO NOTHING;

        -- the four guards and two sites that V106 and later scripts refer to by id
        INSERT INTO security_guards (id, tenant_id, first_name, last_name, psira_number, grade, active, status)
        VALUES ('0708db71-bf26-4022-9930-464713fec05a', zeta, 'Priya',    'Govender', 'PSR-2022-089', 'C', true, 'ACTIVE'),
               ('9573cec7-7207-4cdc-b9dc-8f747fefa52c', zeta, 'Thembisa', 'Mdoda',    'PSR-2022-445', 'C', true, 'ACTIVE'),
               ('6039c07e-b622-431d-9494-4e692c351602', zeta, 'Sipho',    'Nkosi',    'PSR-2024-112', 'D', true, 'ACTIVE'),
               ('f96bb986-aa5c-49fa-96fa-7c8a4b94da57', zeta, 'Andile',   'Zulu',     'PSR-2024-234', 'D', true, 'ACTIVE')
        ON CONFLICT (id) DO NOTHING;

        INSERT INTO security_sites (id, tenant_id, name, active)
        VALUES ('acdce7e8-2557-4dd7-bd9b-70c3cd62e51f', zeta, 'Sandton City Mall North', true),
               ('ca959145-6b86-49df-9c73-8310b1a26b86', zeta, 'Rosebank Tower', true)
        ON CONFLICT (id) DO NOTHING;
    END IF;
END $$;
