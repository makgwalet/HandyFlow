-- ============================================================
-- HandyFlow Accounting Test Data — March & April 2026
-- Tenant: 9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f (Thabo / Zeta Earthmoving)
-- Covers: income, expenses, bank transactions, VAT
-- ============================================================

DO $$
DECLARE
    t_id   UUID := '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f';

    -- Chart of accounts (seeded by ChartOfAccountsSeeder — use standard codes)
    -- INCOME
    acc_sales           UUID;
    acc_services        UUID;
    -- EXPENSE
    acc_salaries        UUID;
    acc_rent            UUID;
    acc_utilities       UUID;
    acc_fuel            UUID;
    acc_telephone       UUID;
    acc_maintenance     UUID;
    -- ASSET
    acc_bank            UUID;
    acc_ar              UUID;
    -- LIABILITY
    acc_vat_control     UUID;
    acc_ap              UUID;
    -- EQUITY
    acc_retained        UUID;

    -- Bank account
    bank_id UUID;

    -- Journal entry IDs
    je1 UUID; je2 UUID; je3 UUID; je4 UUID; je5 UUID;
    je6 UUID; je7 UUID; je8 UUID; je9 UUID; je10 UUID;
    je11 UUID; je12 UUID;
BEGIN
    -- ── Resolve account IDs from chart of accounts ───────────────────────────
    SELECT id INTO acc_sales       FROM acc_accounts WHERE tenant_id = t_id AND account_code = '4100' LIMIT 1;
    SELECT id INTO acc_services    FROM acc_accounts WHERE tenant_id = t_id AND account_code = '4200' LIMIT 1;
    SELECT id INTO acc_salaries    FROM acc_accounts WHERE tenant_id = t_id AND account_code = '5110' LIMIT 1;
    SELECT id INTO acc_rent        FROM acc_accounts WHERE tenant_id = t_id AND account_code = '5120' LIMIT 1;
    SELECT id INTO acc_utilities   FROM acc_accounts WHERE tenant_id = t_id AND account_code = '5130' LIMIT 1;
    SELECT id INTO acc_fuel        FROM acc_accounts WHERE tenant_id = t_id AND account_code = '5150' LIMIT 1;
    SELECT id INTO acc_telephone   FROM acc_accounts WHERE tenant_id = t_id AND account_code = '5160' LIMIT 1;
    SELECT id INTO acc_maintenance FROM acc_accounts WHERE tenant_id = t_id AND account_code = '5170' LIMIT 1;
    SELECT id INTO acc_bank        FROM acc_accounts WHERE tenant_id = t_id AND account_code = '1010' LIMIT 1;
    SELECT id INTO acc_ar          FROM acc_accounts WHERE tenant_id = t_id AND account_code = '1200' LIMIT 1;
    SELECT id INTO acc_vat_control FROM acc_accounts WHERE tenant_id = t_id AND account_code = '2200' LIMIT 1;
    SELECT id INTO acc_ap          FROM acc_accounts WHERE tenant_id = t_id AND account_code = '2100' LIMIT 1;
    SELECT id INTO acc_retained    FROM acc_accounts WHERE tenant_id = t_id AND account_code = '3200' LIMIT 1;

    -- ── Bank account ─────────────────────────────────────────────────────────
    SELECT id INTO bank_id FROM acc_bank_accounts WHERE tenant_id = t_id LIMIT 1;

    -- ============================================================
    -- MARCH 2026
    -- ============================================================

    -- JE-MAR-001: Sales revenue — earthmoving contract payment received
    je1 := gen_random_uuid();
    INSERT INTO acc_journal_entries (id, tenant_id, entry_number, entry_date, description, reference, entry_type, status, total_debit, total_credit, posted_at, created_at, updated_at)
    VALUES (je1, t_id, 'JE-00001', '2026-03-05', 'Earthmoving contract — Merafong Mine Phase 1', 'INV-00001', 'SALES', 'POSTED',
            125000.00, 125000.00, '2026-03-05 08:00:00Z', '2026-03-05 08:00:00Z', '2026-03-05 08:00:00Z');
    INSERT INTO acc_journal_lines (id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
    VALUES
        (gen_random_uuid(), je1, acc_bank,        'Receipt — Merafong Mine',           125000.00,      0.00, 1, now()),
        (gen_random_uuid(), je1, acc_vat_control,  'Output VAT @ 15%',                      0.00,  16304.35, 2, now()),
        (gen_random_uuid(), je1, acc_services,     'Service revenue — earthmoving',          0.00, 108695.65, 3, now());

    -- JE-MAR-002: Salaries March
    je2 := gen_random_uuid();
    INSERT INTO acc_journal_entries (id, tenant_id, entry_number, entry_date, description, reference, entry_type, status, total_debit, total_credit, posted_at, created_at, updated_at)
    VALUES (je2, t_id, 'JE-00002', '2026-03-25', 'Salaries — March 2026', 'PAYROLL-MAR-2026', 'EXPENSE', 'POSTED',
            42000.00, 42000.00, '2026-03-25 09:00:00Z', '2026-03-25 09:00:00Z', '2026-03-25 09:00:00Z');
    INSERT INTO acc_journal_lines (id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
    VALUES
        (gen_random_uuid(), je2, acc_salaries, 'Gross salaries March', 42000.00,     0.00, 1, now()),
        (gen_random_uuid(), je2, acc_bank,     'Salary payments',           0.00, 42000.00, 2, now());

    -- JE-MAR-003: Rent March
    je3 := gen_random_uuid();
    INSERT INTO acc_journal_entries (id, tenant_id, entry_number, entry_date, description, reference, entry_type, status, total_debit, total_credit, posted_at, created_at, updated_at)
    VALUES (je3, t_id, 'JE-00003', '2026-03-01', 'Office & yard rent — March 2026', 'RENT-MAR-2026', 'EXPENSE', 'POSTED',
            12000.00, 12000.00, '2026-03-01 08:00:00Z', '2026-03-01 08:00:00Z', '2026-03-01 08:00:00Z');
    INSERT INTO acc_journal_lines (id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
    VALUES
        (gen_random_uuid(), je3, acc_rent, 'Yard & office rent', 12000.00,     0.00, 1, now()),
        (gen_random_uuid(), je3, acc_bank, 'EFT to landlord',        0.00, 12000.00, 2, now());

    -- JE-MAR-004: Fuel — earthmoving fleet
    je4 := gen_random_uuid();
    INSERT INTO acc_journal_entries (id, tenant_id, entry_number, entry_date, description, reference, entry_type, status, total_debit, total_credit, posted_at, created_at, updated_at)
    VALUES (je4, t_id, 'JE-00004', '2026-03-15', 'Diesel — earthmoving fleet March', 'FUEL-MAR-2026', 'EXPENSE', 'POSTED',
            18500.00, 18500.00, '2026-03-15 10:00:00Z', '2026-03-15 10:00:00Z', '2026-03-15 10:00:00Z');
    INSERT INTO acc_journal_lines (id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
    VALUES
        (gen_random_uuid(), je4, acc_fuel, 'Diesel purchases', 18500.00,     0.00, 1, now()),
        (gen_random_uuid(), je4, acc_bank, 'Payment to fuel depot',  0.00, 18500.00, 2, now());

    -- JE-MAR-005: Second sales invoice — equipment hire
    je5 := gen_random_uuid();
    INSERT INTO acc_journal_entries (id, tenant_id, entry_number, entry_date, description, reference, entry_type, status, total_debit, total_credit, posted_at, created_at, updated_at)
    VALUES (je5, t_id, 'JE-00005', '2026-03-18', 'Equipment hire — Acme Construction', 'INV-00002', 'SALES', 'POSTED',
            57500.00, 57500.00, '2026-03-18 08:00:00Z', '2026-03-18 08:00:00Z', '2026-03-18 08:00:00Z');
    INSERT INTO acc_journal_lines (id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
    VALUES
        (gen_random_uuid(), je5, acc_ar,           'Acme Construction — 30 day terms',  57500.00,      0.00, 1, now()),
        (gen_random_uuid(), je5, acc_vat_control,  'Output VAT @ 15%',                      0.00,   7500.00, 2, now()),
        (gen_random_uuid(), je5, acc_sales,        'Equipment hire revenue',                 0.00,  50000.00, 3, now());

    -- JE-MAR-006: Utilities March
    je6 := gen_random_uuid();
    INSERT INTO acc_journal_entries (id, tenant_id, entry_number, entry_date, description, reference, entry_type, status, total_debit, total_credit, posted_at, created_at, updated_at)
    VALUES (je6, t_id, 'JE-00006', '2026-03-20', 'Water & electricity — March 2026', 'UTIL-MAR-2026', 'EXPENSE', 'POSTED',
            4800.00, 4800.00, '2026-03-20 09:00:00Z', '2026-03-20 09:00:00Z', '2026-03-20 09:00:00Z');
    INSERT INTO acc_journal_lines (id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
    VALUES
        (gen_random_uuid(), je6, acc_utilities, 'Utilities March', 4800.00,    0.00, 1, now()),
        (gen_random_uuid(), je6, acc_bank,      'EFT to municipality', 0.00, 4800.00, 2, now());

    -- ============================================================
    -- APRIL 2026
    -- ============================================================

    -- JE-APR-001: Large contract — road rehabilitation
    je7 := gen_random_uuid();
    INSERT INTO acc_journal_entries (id, tenant_id, entry_number, entry_date, description, reference, entry_type, status, total_debit, total_credit, posted_at, created_at, updated_at)
    VALUES (je7, t_id, 'JE-00007', '2026-04-03', 'Road rehabilitation — Carletonville Phase 2', 'INV-00003', 'SALES', 'POSTED',
            230000.00, 230000.00, '2026-04-03 08:00:00Z', '2026-04-03 08:00:00Z', '2026-04-03 08:00:00Z');
    INSERT INTO acc_journal_lines (id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
    VALUES
        (gen_random_uuid(), je7, acc_bank,        'Receipt — road rehab contract',  230000.00,      0.00, 1, now()),
        (gen_random_uuid(), je7, acc_vat_control,  'Output VAT @ 15%',                   0.00,  30000.00, 2, now()),
        (gen_random_uuid(), je7, acc_services,     'Road rehabilitation revenue',         0.00, 200000.00, 3, now());

    -- JE-APR-002: Salaries April
    je8 := gen_random_uuid();
    INSERT INTO acc_journal_entries (id, tenant_id, entry_number, entry_date, description, reference, entry_type, status, total_debit, total_credit, posted_at, created_at, updated_at)
    VALUES (je8, t_id, 'JE-00008', '2026-04-25', 'Salaries — April 2026', 'PAYROLL-APR-2026', 'EXPENSE', 'POSTED',
            42000.00, 42000.00, '2026-04-25 09:00:00Z', '2026-04-25 09:00:00Z', '2026-04-25 09:00:00Z');
    INSERT INTO acc_journal_lines (id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
    VALUES
        (gen_random_uuid(), je8, acc_salaries, 'Gross salaries April', 42000.00,     0.00, 1, now()),
        (gen_random_uuid(), je8, acc_bank,     'Salary payments',           0.00, 42000.00, 2, now());

    -- JE-APR-003: Rent April
    je9 := gen_random_uuid();
    INSERT INTO acc_journal_entries (id, tenant_id, entry_number, entry_date, description, reference, entry_type, status, total_debit, total_credit, posted_at, created_at, updated_at)
    VALUES (je9, t_id, 'JE-00009', '2026-04-01', 'Office & yard rent — April 2026', 'RENT-APR-2026', 'EXPENSE', 'POSTED',
            12000.00, 12000.00, '2026-04-01 08:00:00Z', '2026-04-01 08:00:00Z', '2026-04-01 08:00:00Z');
    INSERT INTO acc_journal_lines (id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
    VALUES
        (gen_random_uuid(), je9, acc_rent, 'Yard & office rent', 12000.00,     0.00, 1, now()),
        (gen_random_uuid(), je9, acc_bank, 'EFT to landlord',        0.00, 12000.00, 2, now());

    -- JE-APR-004: Fuel April (higher — bigger contract)
    je10 := gen_random_uuid();
    INSERT INTO acc_journal_entries (id, tenant_id, entry_number, entry_date, description, reference, entry_type, status, total_debit, total_credit, posted_at, created_at, updated_at)
    VALUES (je10, t_id, 'JE-00010', '2026-04-12', 'Diesel — earthmoving fleet April', 'FUEL-APR-2026', 'EXPENSE', 'POSTED',
            31200.00, 31200.00, '2026-04-12 10:00:00Z', '2026-04-12 10:00:00Z', '2026-04-12 10:00:00Z');
    INSERT INTO acc_journal_lines (id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
    VALUES
        (gen_random_uuid(), je10, acc_fuel, 'Diesel purchases April', 31200.00,     0.00, 1, now()),
        (gen_random_uuid(), je10, acc_bank, 'Payment to fuel depot',      0.00, 31200.00, 2, now());

    -- JE-APR-005: Equipment maintenance
    je11 := gen_random_uuid();
    INSERT INTO acc_journal_entries (id, tenant_id, entry_number, entry_date, description, reference, entry_type, status, total_debit, total_credit, posted_at, created_at, updated_at)
    VALUES (je11, t_id, 'JE-00011', '2026-04-17', 'Excavator service & repairs', 'MAINT-APR-2026', 'EXPENSE', 'POSTED',
            9750.00, 9750.00, '2026-04-17 11:00:00Z', '2026-04-17 11:00:00Z', '2026-04-17 11:00:00Z');
    INSERT INTO acc_journal_lines (id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
    VALUES
        (gen_random_uuid(), je11, acc_maintenance, 'Excavator 6000hr service',  9750.00,    0.00, 1, now()),
        (gen_random_uuid(), je11, acc_bank,        'Payment to Mantis Equipment', 0.00, 9750.00, 2, now());

    -- JE-APR-006: Telephone & internet April
    je12 := gen_random_uuid();
    INSERT INTO acc_journal_entries (id, tenant_id, entry_number, entry_date, description, reference, entry_type, status, total_debit, total_credit, posted_at, created_at, updated_at)
    VALUES (je12, t_id, 'JE-00012', '2026-04-05', 'Telephone & fibre — April 2026', 'TEL-APR-2026', 'EXPENSE', 'POSTED',
            2890.00, 2890.00, '2026-04-05 09:00:00Z', '2026-04-05 09:00:00Z', '2026-04-05 09:00:00Z');
    INSERT INTO acc_journal_lines (id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
    VALUES
        (gen_random_uuid(), je12, acc_telephone, 'Vodacom & Vox fibre',    2890.00,    0.00, 1, now()),
        (gen_random_uuid(), je12, acc_bank,      'Debit order payments',       0.00, 2890.00, 2, now());

    -- ── Bank transactions (for Bank Accounts tab) ─────────────────────────────
    IF bank_id IS NOT NULL THEN
        INSERT INTO acc_bank_transactions (id, bank_account_id, transaction_date, description, reference, type, amount, running_balance, created_at)
        VALUES
            -- March
            (gen_random_uuid(), bank_id, '2026-03-01', 'Rent payment — March',        'RENT-MAR-2026',    'DEBIT',   12000.00, 0, now()),
            (gen_random_uuid(), bank_id, '2026-03-05', 'Merafong Mine — contract',    'INV-00001',        'CREDIT', 125000.00, 0, now()),
            (gen_random_uuid(), bank_id, '2026-03-15', 'Diesel — fleet March',        'FUEL-MAR-2026',    'DEBIT',   18500.00, 0, now()),
            (gen_random_uuid(), bank_id, '2026-03-20', 'Municipality utilities',      'UTIL-MAR-2026',    'DEBIT',    4800.00, 0, now()),
            (gen_random_uuid(), bank_id, '2026-03-25', 'Payroll March',               'PAYROLL-MAR-2026', 'DEBIT',   42000.00, 0, now()),
            -- April
            (gen_random_uuid(), bank_id, '2026-04-01', 'Rent payment — April',        'RENT-APR-2026',    'DEBIT',   12000.00, 0, now()),
            (gen_random_uuid(), bank_id, '2026-04-03', 'Road rehab — Carletonville',  'INV-00003',        'CREDIT', 230000.00, 0, now()),
            (gen_random_uuid(), bank_id, '2026-04-05', 'Vodacom & Vox debit order',   'TEL-APR-2026',     'DEBIT',    2890.00, 0, now()),
            (gen_random_uuid(), bank_id, '2026-04-12', 'Diesel — fleet April',        'FUEL-APR-2026',    'DEBIT',   31200.00, 0, now()),
            (gen_random_uuid(), bank_id, '2026-04-17', 'Mantis Equipment — service',  'MAINT-APR-2026',   'DEBIT',    9750.00, 0, now()),
            (gen_random_uuid(), bank_id, '2026-04-25', 'Payroll April',               'PAYROLL-APR-2026', 'DEBIT',   42000.00, 0, now());
    END IF;

    -- ── Journal sequences update (only if table exists from V74) ───────────────
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'acc_journal_sequences') THEN
        INSERT INTO acc_journal_sequences (tenant_id, last_sequence)
        VALUES (t_id, 12)
        ON CONFLICT (tenant_id) DO UPDATE SET last_sequence = GREATEST(acc_journal_sequences.last_sequence, 12);
    END IF;

END $$;