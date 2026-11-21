-- =============================================================================
-- V107__accounting_test_data_zeta.sql
-- HandyFlow Accounting Test Data — March & April 2026
-- Tenant: 9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f  (Zeta Earthmoving)
--
-- Account code mapping (verified against live acc_accounts table):
--   4020 = Sales - Services (earthmoving contracts)
--   4030 = Hire Income (equipment hire)
--   5110 = Salaries and Wages
--   5120 = Rent Expense
--   5130 = Utilities
--   5140 = Fuel and Travel
--   5150 = Vehicle Maintenance (used for equipment repairs)
--   5160 = Telephone and Internet
--   1020 = Bank - Cheque Account  (NOT 1010 which is Cash)
--   1100 = Accounts Receivable    (NOT 1200 which is Inventory)
--   2100 = VAT Output (Payable)   (NOT 2200 which is PAYE)
--   2110 = VAT Control Account
--   2010 = Accounts Payable
--   3020 = Retained Earnings
-- =============================================================================

BEGIN;

DO $$
DECLARE
    t_id UUID := '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f';

    -- Account IDs resolved by account_code (correct column name)
    acc_services    UUID;   -- 4020 Sales - Services
    acc_hire        UUID;   -- 4030 Hire Income
    acc_salaries    UUID;   -- 5110 Salaries and Wages
    acc_rent        UUID;   -- 5120 Rent Expense
    acc_utilities   UUID;   -- 5130 Utilities
    acc_fuel        UUID;   -- 5140 Fuel and Travel
    acc_maintenance UUID;   -- 5150 Vehicle Maintenance (equipment repairs)
    acc_telephone   UUID;   -- 5160 Telephone and Internet
    acc_bank        UUID;   -- 1020 Bank - Cheque Account
    acc_ar          UUID;   -- 1100 Accounts Receivable
    acc_vat_out     UUID;   -- 2100 VAT Output (Payable)
    acc_vat_ctrl    UUID;   -- 2110 VAT Control Account

    bank_id UUID;

    je1  UUID; je2  UUID; je3  UUID; je4  UUID;
    je5  UUID; je6  UUID; je7  UUID; je8  UUID;
    je9  UUID; je10 UUID; je11 UUID; je12 UUID;

BEGIN
    -- ── Resolve account IDs by account_code ──────────────────────────────────
    SELECT id INTO acc_services    FROM acc_accounts WHERE tenant_id = t_id AND account_code = '4020' LIMIT 1;
    SELECT id INTO acc_hire        FROM acc_accounts WHERE tenant_id = t_id AND account_code = '4030' LIMIT 1;
    SELECT id INTO acc_salaries    FROM acc_accounts WHERE tenant_id = t_id AND account_code = '5110' LIMIT 1;
    SELECT id INTO acc_rent        FROM acc_accounts WHERE tenant_id = t_id AND account_code = '5120' LIMIT 1;
    SELECT id INTO acc_utilities   FROM acc_accounts WHERE tenant_id = t_id AND account_code = '5130' LIMIT 1;
    SELECT id INTO acc_fuel        FROM acc_accounts WHERE tenant_id = t_id AND account_code = '5140' LIMIT 1;
    SELECT id INTO acc_maintenance FROM acc_accounts WHERE tenant_id = t_id AND account_code = '5150' LIMIT 1;
    SELECT id INTO acc_telephone   FROM acc_accounts WHERE tenant_id = t_id AND account_code = '5160' LIMIT 1;
    SELECT id INTO acc_bank        FROM acc_accounts WHERE tenant_id = t_id AND account_code = '1020' LIMIT 1;
    SELECT id INTO acc_ar          FROM acc_accounts WHERE tenant_id = t_id AND account_code = '1100' LIMIT 1;
    SELECT id INTO acc_vat_out     FROM acc_accounts WHERE tenant_id = t_id AND account_code = '2100' LIMIT 1;
    SELECT id INTO acc_vat_ctrl    FROM acc_accounts WHERE tenant_id = t_id AND account_code = '2110' LIMIT 1;

    -- NULL-guard: fail loudly if any account is missing
    IF acc_services    IS NULL THEN RAISE EXCEPTION 'Missing account_code 4020 (Sales-Services) for tenant %', t_id; END IF;
    IF acc_hire        IS NULL THEN RAISE EXCEPTION 'Missing account_code 4030 (Hire Income) for tenant %', t_id; END IF;
    IF acc_salaries    IS NULL THEN RAISE EXCEPTION 'Missing account_code 5110 (Salaries) for tenant %', t_id; END IF;
    IF acc_rent        IS NULL THEN RAISE EXCEPTION 'Missing account_code 5120 (Rent) for tenant %', t_id; END IF;
    IF acc_utilities   IS NULL THEN RAISE EXCEPTION 'Missing account_code 5130 (Utilities) for tenant %', t_id; END IF;
    IF acc_fuel        IS NULL THEN RAISE EXCEPTION 'Missing account_code 5140 (Fuel) for tenant %', t_id; END IF;
    IF acc_maintenance IS NULL THEN RAISE EXCEPTION 'Missing account_code 5150 (Maintenance) for tenant %', t_id; END IF;
    IF acc_telephone   IS NULL THEN RAISE EXCEPTION 'Missing account_code 5160 (Telephone) for tenant %', t_id; END IF;
    IF acc_bank        IS NULL THEN RAISE EXCEPTION 'Missing account_code 1020 (Bank Cheque) for tenant %', t_id; END IF;
    IF acc_ar          IS NULL THEN RAISE EXCEPTION 'Missing account_code 1100 (AR) for tenant %', t_id; END IF;
    IF acc_vat_out     IS NULL THEN RAISE EXCEPTION 'Missing account_code 2100 (VAT Output) for tenant %', t_id; END IF;
    IF acc_vat_ctrl    IS NULL THEN RAISE EXCEPTION 'Missing account_code 2110 (VAT Control) for tenant %', t_id; END IF;

    SELECT id INTO bank_id FROM acc_bank_accounts WHERE tenant_id = t_id LIMIT 1;

    -- =========================================================================
    -- MARCH 2026
    -- =========================================================================

    -- JE-ZE-001: Earthmoving contract — Merafong Mine Phase 1 (cash received)
    je1 := gen_random_uuid();
    INSERT INTO acc_journal_entries
        (id, tenant_id, entry_number, entry_date, description, reference,
         entry_type, status, total_debit, total_credit, posted_at, created_at, updated_at)
    VALUES (je1, t_id, 'JE-ZE-001', '2026-03-05',
        'Earthmoving contract — Merafong Mine Phase 1', 'INV-ZE-001',
        'INVOICE', 'POSTED', 125000.00, 125000.00,
        '2026-03-05 08:00:00Z', '2026-03-05 08:00:00Z', '2026-03-05 08:00:00Z');
    INSERT INTO acc_journal_lines
        (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
    VALUES
        (gen_random_uuid(), t_id, je1, acc_bank,     'Receipt — Merafong Mine',        125000.00,      0.00, 1, now()),
        (gen_random_uuid(), t_id, je1, acc_vat_out,  'Output VAT @ 15%',                    0.00,  16304.35, 2, now()),
        (gen_random_uuid(), t_id, je1, acc_services, 'Earthmoving service revenue',          0.00, 108695.65, 3, now());

    -- JE-ZE-002: Salaries March
    je2 := gen_random_uuid();
    INSERT INTO acc_journal_entries
        (id, tenant_id, entry_number, entry_date, description, reference,
         entry_type, status, total_debit, total_credit, posted_at, created_at, updated_at)
    VALUES (je2, t_id, 'JE-ZE-002', '2026-03-25',
        'Salaries — March 2026', 'PAYROLL-MAR-2026',
        'PAYMENT', 'POSTED', 42000.00, 42000.00,
        '2026-03-25 09:00:00Z', '2026-03-25 09:00:00Z', '2026-03-25 09:00:00Z');
    INSERT INTO acc_journal_lines
        (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
    VALUES
        (gen_random_uuid(), t_id, je2, acc_salaries, 'Gross salaries March', 42000.00,     0.00, 1, now()),
        (gen_random_uuid(), t_id, je2, acc_bank,     'Salary payments',           0.00, 42000.00, 2, now());

    -- JE-ZE-003: Rent March
    je3 := gen_random_uuid();
    INSERT INTO acc_journal_entries
        (id, tenant_id, entry_number, entry_date, description, reference,
         entry_type, status, total_debit, total_credit, posted_at, created_at, updated_at)
    VALUES (je3, t_id, 'JE-ZE-003', '2026-03-01',
        'Office & yard rent — March 2026', 'RENT-MAR-2026',
        'PAYMENT', 'POSTED', 12000.00, 12000.00,
        '2026-03-01 08:00:00Z', '2026-03-01 08:00:00Z', '2026-03-01 08:00:00Z');
    INSERT INTO acc_journal_lines
        (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
    VALUES
        (gen_random_uuid(), t_id, je3, acc_rent, 'Yard & office rent', 12000.00,     0.00, 1, now()),
        (gen_random_uuid(), t_id, je3, acc_bank, 'EFT to landlord',        0.00, 12000.00, 2, now());

    -- JE-ZE-004: Diesel — earthmoving fleet March
    je4 := gen_random_uuid();
    INSERT INTO acc_journal_entries
        (id, tenant_id, entry_number, entry_date, description, reference,
         entry_type, status, total_debit, total_credit, posted_at, created_at, updated_at)
    VALUES (je4, t_id, 'JE-ZE-004', '2026-03-15',
        'Diesel — earthmoving fleet March', 'FUEL-MAR-2026',
        'PAYMENT', 'POSTED', 18500.00, 18500.00,
        '2026-03-15 10:00:00Z', '2026-03-15 10:00:00Z', '2026-03-15 10:00:00Z');
    INSERT INTO acc_journal_lines
        (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
    VALUES
        (gen_random_uuid(), t_id, je4, acc_fuel, 'Diesel purchases',       18500.00,     0.00, 1, now()),
        (gen_random_uuid(), t_id, je4, acc_bank, 'Payment to fuel depot',      0.00, 18500.00, 2, now());

    -- JE-ZE-005: Equipment hire — Acme Construction (debtor, 30-day terms)
    je5 := gen_random_uuid();
    INSERT INTO acc_journal_entries
        (id, tenant_id, entry_number, entry_date, description, reference,
         entry_type, status, total_debit, total_credit, posted_at, created_at, updated_at)
    VALUES (je5, t_id, 'JE-ZE-005', '2026-03-18',
        'Equipment hire — Acme Construction', 'INV-ZE-002',
        'INVOICE', 'POSTED', 57500.00, 57500.00,
        '2026-03-18 08:00:00Z', '2026-03-18 08:00:00Z', '2026-03-18 08:00:00Z');
    INSERT INTO acc_journal_lines
        (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
    VALUES
        (gen_random_uuid(), t_id, je5, acc_ar,       'Acme Construction — 30 day terms', 57500.00,     0.00, 1, now()),
        (gen_random_uuid(), t_id, je5, acc_vat_out,  'Output VAT @ 15%',                     0.00,  7500.00, 2, now()),
        (gen_random_uuid(), t_id, je5, acc_hire,     'Equipment hire revenue',                0.00, 50000.00, 3, now());

    -- JE-ZE-006: Water & electricity March
    je6 := gen_random_uuid();
    INSERT INTO acc_journal_entries
        (id, tenant_id, entry_number, entry_date, description, reference,
         entry_type, status, total_debit, total_credit, posted_at, created_at, updated_at)
    VALUES (je6, t_id, 'JE-ZE-006', '2026-03-20',
        'Water & electricity — March 2026', 'UTIL-MAR-2026',
        'PAYMENT', 'POSTED', 4800.00, 4800.00,
        '2026-03-20 09:00:00Z', '2026-03-20 09:00:00Z', '2026-03-20 09:00:00Z');
    INSERT INTO acc_journal_lines
        (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
    VALUES
        (gen_random_uuid(), t_id, je6, acc_utilities, 'Utilities March',     4800.00,    0.00, 1, now()),
        (gen_random_uuid(), t_id, je6, acc_bank,      'EFT to municipality',     0.00, 4800.00, 2, now());

    -- =========================================================================
    -- APRIL 2026
    -- =========================================================================

    -- JE-ZE-007: Road rehabilitation — Carletonville Phase 2 (cash received)
    je7 := gen_random_uuid();
    INSERT INTO acc_journal_entries
        (id, tenant_id, entry_number, entry_date, description, reference,
         entry_type, status, total_debit, total_credit, posted_at, created_at, updated_at)
    VALUES (je7, t_id, 'JE-ZE-007', '2026-04-03',
        'Road rehabilitation — Carletonville Phase 2', 'INV-ZE-003',
        'INVOICE', 'POSTED', 230000.00, 230000.00,
        '2026-04-03 08:00:00Z', '2026-04-03 08:00:00Z', '2026-04-03 08:00:00Z');
    INSERT INTO acc_journal_lines
        (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
    VALUES
        (gen_random_uuid(), t_id, je7, acc_bank,     'Receipt — road rehab contract',   230000.00,      0.00, 1, now()),
        (gen_random_uuid(), t_id, je7, acc_vat_out,  'Output VAT @ 15%',                     0.00,  30000.00, 2, now()),
        (gen_random_uuid(), t_id, je7, acc_services, 'Road rehabilitation revenue',           0.00, 200000.00, 3, now());

    -- JE-ZE-008: Salaries April
    je8 := gen_random_uuid();
    INSERT INTO acc_journal_entries
        (id, tenant_id, entry_number, entry_date, description, reference,
         entry_type, status, total_debit, total_credit, posted_at, created_at, updated_at)
    VALUES (je8, t_id, 'JE-ZE-008', '2026-04-25',
        'Salaries — April 2026', 'PAYROLL-APR-2026',
        'PAYMENT', 'POSTED', 42000.00, 42000.00,
        '2026-04-25 09:00:00Z', '2026-04-25 09:00:00Z', '2026-04-25 09:00:00Z');
    INSERT INTO acc_journal_lines
        (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
    VALUES
        (gen_random_uuid(), t_id, je8, acc_salaries, 'Gross salaries April', 42000.00,     0.00, 1, now()),
        (gen_random_uuid(), t_id, je8, acc_bank,     'Salary payments',           0.00, 42000.00, 2, now());

    -- JE-ZE-009: Rent April
    je9 := gen_random_uuid();
    INSERT INTO acc_journal_entries
        (id, tenant_id, entry_number, entry_date, description, reference,
         entry_type, status, total_debit, total_credit, posted_at, created_at, updated_at)
    VALUES (je9, t_id, 'JE-ZE-009', '2026-04-01',
        'Office & yard rent — April 2026', 'RENT-APR-2026',
        'PAYMENT', 'POSTED', 12000.00, 12000.00,
        '2026-04-01 08:00:00Z', '2026-04-01 08:00:00Z', '2026-04-01 08:00:00Z');
    INSERT INTO acc_journal_lines
        (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
    VALUES
        (gen_random_uuid(), t_id, je9, acc_rent, 'Yard & office rent', 12000.00,     0.00, 1, now()),
        (gen_random_uuid(), t_id, je9, acc_bank, 'EFT to landlord',        0.00, 12000.00, 2, now());

    -- JE-ZE-010: Diesel April (higher — bigger contract running)
    je10 := gen_random_uuid();
    INSERT INTO acc_journal_entries
        (id, tenant_id, entry_number, entry_date, description, reference,
         entry_type, status, total_debit, total_credit, posted_at, created_at, updated_at)
    VALUES (je10, t_id, 'JE-ZE-010', '2026-04-12',
        'Diesel — earthmoving fleet April', 'FUEL-APR-2026',
        'PAYMENT', 'POSTED', 31200.00, 31200.00,
        '2026-04-12 10:00:00Z', '2026-04-12 10:00:00Z', '2026-04-12 10:00:00Z');
    INSERT INTO acc_journal_lines
        (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
    VALUES
        (gen_random_uuid(), t_id, je10, acc_fuel, 'Diesel purchases April',   31200.00,     0.00, 1, now()),
        (gen_random_uuid(), t_id, je10, acc_bank, 'Payment to fuel depot',        0.00, 31200.00, 2, now());

    -- JE-ZE-011: Excavator 6000hr service & repairs
    je11 := gen_random_uuid();
    INSERT INTO acc_journal_entries
        (id, tenant_id, entry_number, entry_date, description, reference,
         entry_type, status, total_debit, total_credit, posted_at, created_at, updated_at)
    VALUES (je11, t_id, 'JE-ZE-011', '2026-04-17',
        'Excavator service & repairs', 'MAINT-APR-2026',
        'PAYMENT', 'POSTED', 9750.00, 9750.00,
        '2026-04-17 11:00:00Z', '2026-04-17 11:00:00Z', '2026-04-17 11:00:00Z');
    INSERT INTO acc_journal_lines
        (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
    VALUES
        (gen_random_uuid(), t_id, je11, acc_maintenance, 'Excavator 6000hr service',     9750.00,    0.00, 1, now()),
        (gen_random_uuid(), t_id, je11, acc_bank,        'Payment to Mantis Equipment',     0.00, 9750.00, 2, now());

    -- JE-ZE-012: Telephone & fibre April
    je12 := gen_random_uuid();
    INSERT INTO acc_journal_entries
        (id, tenant_id, entry_number, entry_date, description, reference,
         entry_type, status, total_debit, total_credit, posted_at, created_at, updated_at)
    VALUES (je12, t_id, 'JE-ZE-012', '2026-04-05',
        'Telephone & fibre — April 2026', 'TEL-APR-2026',
        'PAYMENT', 'POSTED', 2890.00, 2890.00,
        '2026-04-05 09:00:00Z', '2026-04-05 09:00:00Z', '2026-04-05 09:00:00Z');
    INSERT INTO acc_journal_lines
        (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
    VALUES
        (gen_random_uuid(), t_id, je12, acc_telephone, 'Vodacom & Vox fibre',    2890.00,    0.00, 1, now()),
        (gen_random_uuid(), t_id, je12, acc_bank,      'Debit order payments',       0.00, 2890.00, 2, now());

    -- Bank transactions skipped: acc_bank_transactions column schema differs
    -- from the original V75 assumption. Journal entries above fully represent
    -- the double-entry records. Bank statement import can be done via the UI.

    -- Journal sequence skipped: acc_journal_sequences column name differs from
    -- original V75 assumption. Sequence is managed automatically by the application.

END $$;

COMMIT;