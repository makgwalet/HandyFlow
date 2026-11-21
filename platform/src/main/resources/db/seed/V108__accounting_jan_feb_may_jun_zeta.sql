-- =============================================================================
-- V108__accounting_jan_feb_may_jun_zeta.sql
-- Zeta Earthmoving (Pty) Ltd — 9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f
-- Additional months: January, February, May, June 2026
--
-- Context: Earthmoving & road rehabilitation company, Gauteng.
--   Jan/Feb = off-season (minimal new contracts, recurring fixed costs)
--   May     = peak construction season (large road rehab + mine contract)
--   Jun     = current month (partial, mix of POSTED + DRAFT)
--
-- Account codes (verified against live acc_accounts):
--   4020=Sales-Services  4030=Hire Income
--   5110=Salaries        5120=Rent       5130=Utilities
--   5140=Fuel            5150=Veh.Maint  5160=Telephone
--   1020=Bank-Cheque     1100=AR
--   2100=VAT Output      5190=Prof.Fees  5210=Bank Charges
--   5220=Depreciation    5200=Insurance  5180=Marketing
-- =============================================================================

BEGIN;

DO $$
DECLARE
    t_id UUID := '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f';

    acc_services    UUID;
    acc_hire        UUID;
    acc_salaries    UUID;
    acc_rent        UUID;
    acc_utilities   UUID;
    acc_fuel        UUID;
    acc_maintenance UUID;
    acc_telephone   UUID;
    acc_bank        UUID;
    acc_ar          UUID;
    acc_vat_out     UUID;
    acc_prof_fees   UUID;
    acc_bank_chg    UUID;
    acc_depreciation UUID;
    acc_insurance   UUID;
    acc_marketing   UUID;

    -- January entries
    jj01 UUID; jj02 UUID; jj03 UUID; jj04 UUID; jj05 UUID; jj06 UUID; jj07 UUID;
    -- February entries
    jf01 UUID; jf02 UUID; jf03 UUID; jf04 UUID; jf05 UUID; jf06 UUID; jf07 UUID; jf08 UUID;
    -- May entries
    jm01 UUID; jm02 UUID; jm03 UUID; jm04 UUID; jm05 UUID; jm06 UUID; jm07 UUID; jm08 UUID;
    -- June entries
    jg01 UUID; jg02 UUID; jg03 UUID; jg04 UUID; jg05 UUID; jg06 UUID;

BEGIN
    -- Resolve account IDs
    SELECT id INTO acc_services    FROM acc_accounts WHERE tenant_id=t_id AND account_code='4020' LIMIT 1;
    SELECT id INTO acc_hire        FROM acc_accounts WHERE tenant_id=t_id AND account_code='4030' LIMIT 1;
    SELECT id INTO acc_salaries    FROM acc_accounts WHERE tenant_id=t_id AND account_code='5110' LIMIT 1;
    SELECT id INTO acc_rent        FROM acc_accounts WHERE tenant_id=t_id AND account_code='5120' LIMIT 1;
    SELECT id INTO acc_utilities   FROM acc_accounts WHERE tenant_id=t_id AND account_code='5130' LIMIT 1;
    SELECT id INTO acc_fuel        FROM acc_accounts WHERE tenant_id=t_id AND account_code='5140' LIMIT 1;
    SELECT id INTO acc_maintenance FROM acc_accounts WHERE tenant_id=t_id AND account_code='5150' LIMIT 1;
    SELECT id INTO acc_telephone   FROM acc_accounts WHERE tenant_id=t_id AND account_code='5160' LIMIT 1;
    SELECT id INTO acc_bank        FROM acc_accounts WHERE tenant_id=t_id AND account_code='1020' LIMIT 1;
    SELECT id INTO acc_ar          FROM acc_accounts WHERE tenant_id=t_id AND account_code='1100' LIMIT 1;
    SELECT id INTO acc_vat_out     FROM acc_accounts WHERE tenant_id=t_id AND account_code='2100' LIMIT 1;
    SELECT id INTO acc_prof_fees   FROM acc_accounts WHERE tenant_id=t_id AND account_code='5190' LIMIT 1;
    SELECT id INTO acc_bank_chg    FROM acc_accounts WHERE tenant_id=t_id AND account_code='5210' LIMIT 1;
    SELECT id INTO acc_depreciation FROM acc_accounts WHERE tenant_id=t_id AND account_code='5220' LIMIT 1;
    SELECT id INTO acc_insurance   FROM acc_accounts WHERE tenant_id=t_id AND account_code='5200' LIMIT 1;
    SELECT id INTO acc_marketing   FROM acc_accounts WHERE tenant_id=t_id AND account_code='5180' LIMIT 1;

    IF acc_services IS NULL THEN RAISE EXCEPTION 'Missing 4020'; END IF;
    IF acc_salaries IS NULL THEN RAISE EXCEPTION 'Missing 5110'; END IF;
    IF acc_bank     IS NULL THEN RAISE EXCEPTION 'Missing 1020'; END IF;
    IF acc_vat_out  IS NULL THEN RAISE EXCEPTION 'Missing 2100'; END IF;

    -- =========================================================================
    -- JANUARY 2026 — Off-season: one small hire, fixed costs, annual insurance
    -- =========================================================================

    -- JE-ZE-J01: Equipment hire — small site work, Vereeniging municipality
    jj01 := gen_random_uuid();
    INSERT INTO acc_journal_entries (id,tenant_id,entry_number,entry_date,description,reference,entry_type,status,total_debit,total_credit,posted_at,created_at,updated_at)
    VALUES (jj01,t_id,'JE-ZE-J01','2026-01-08','Equipment hire — Vereeniging Municipality','INV-ZE-J01','INVOICE','POSTED',34500.00,34500.00,'2026-01-08 08:00:00','2026-01-08 08:00:00','2026-01-08 08:00:00');
    INSERT INTO acc_journal_lines (id,tenant_id,journal_entry_id,account_id,description,debit_amount,credit_amount,sort_order,created_at) VALUES
    (gen_random_uuid(),t_id,jj01,acc_ar,         'Vereeniging Muni — 30 day terms',34500.00,     0.00,1,now()),
    (gen_random_uuid(),t_id,jj01,acc_vat_out,    'Output VAT @ 15%',                   0.00,  4500.00,2,now()),
    (gen_random_uuid(),t_id,jj01,acc_hire,        'Equipment hire revenue',              0.00, 30000.00,3,now());

    -- JE-ZE-J02: Annual insurance premium
    jj02 := gen_random_uuid();
    INSERT INTO acc_journal_entries (id,tenant_id,entry_number,entry_date,description,reference,entry_type,status,total_debit,total_credit,posted_at,created_at,updated_at)
    VALUES (jj02,t_id,'JE-ZE-J02','2026-01-02','Annual plant & equipment insurance — 2026','INS-2026','PAYMENT','POSTED',28750.00,28750.00,'2026-01-02 09:00:00','2026-01-02 09:00:00','2026-01-02 09:00:00');
    INSERT INTO acc_journal_lines (id,tenant_id,journal_entry_id,account_id,description,debit_amount,credit_amount,sort_order,created_at) VALUES
    (gen_random_uuid(),t_id,jj02,acc_insurance,'Plant & fleet insurance premium',28750.00,     0.00,1,now()),
    (gen_random_uuid(),t_id,jj02,acc_bank,     'EFT to Santam',                      0.00,28750.00,2,now());

    -- JE-ZE-J03: Salaries January
    jj03 := gen_random_uuid();
    INSERT INTO acc_journal_entries (id,tenant_id,entry_number,entry_date,description,reference,entry_type,status,total_debit,total_credit,posted_at,created_at,updated_at)
    VALUES (jj03,t_id,'JE-ZE-J03','2026-01-26','Salaries — January 2026','PAYROLL-JAN-2026','PAYMENT','POSTED',42000.00,42000.00,'2026-01-26 09:00:00','2026-01-26 09:00:00','2026-01-26 09:00:00');
    INSERT INTO acc_journal_lines (id,tenant_id,journal_entry_id,account_id,description,debit_amount,credit_amount,sort_order,created_at) VALUES
    (gen_random_uuid(),t_id,jj03,acc_salaries,'Gross salaries January',42000.00,    0.00,1,now()),
    (gen_random_uuid(),t_id,jj03,acc_bank,    'Salary payments',            0.00,42000.00,2,now());

    -- JE-ZE-J04: Rent January
    jj04 := gen_random_uuid();
    INSERT INTO acc_journal_entries (id,tenant_id,entry_number,entry_date,description,reference,entry_type,status,total_debit,total_credit,posted_at,created_at,updated_at)
    VALUES (jj04,t_id,'JE-ZE-J04','2026-01-02','Office & yard rent — January 2026','RENT-JAN-2026','PAYMENT','POSTED',12000.00,12000.00,'2026-01-02 08:00:00','2026-01-02 08:00:00','2026-01-02 08:00:00');
    INSERT INTO acc_journal_lines (id,tenant_id,journal_entry_id,account_id,description,debit_amount,credit_amount,sort_order,created_at) VALUES
    (gen_random_uuid(),t_id,jj04,acc_rent,'Yard & office rent',12000.00,    0.00,1,now()),
    (gen_random_uuid(),t_id,jj04,acc_bank,'EFT to landlord',       0.00,12000.00,2,now());

    -- JE-ZE-J05: Fuel January (low — no active contracts)
    jj05 := gen_random_uuid();
    INSERT INTO acc_journal_entries (id,tenant_id,entry_number,entry_date,description,reference,entry_type,status,total_debit,total_credit,posted_at,created_at,updated_at)
    VALUES (jj05,t_id,'JE-ZE-J05','2026-01-15','Diesel — delivery & standby January','FUEL-JAN-2026','PAYMENT','POSTED',6200.00,6200.00,'2026-01-15 10:00:00','2026-01-15 10:00:00','2026-01-15 10:00:00');
    INSERT INTO acc_journal_lines (id,tenant_id,journal_entry_id,account_id,description,debit_amount,credit_amount,sort_order,created_at) VALUES
    (gen_random_uuid(),t_id,jj05,acc_fuel,'Diesel — standby fleet', 6200.00,   0.00,1,now()),
    (gen_random_uuid(),t_id,jj05,acc_bank,'Payment to fuel depot',      0.00,6200.00,2,now());

    -- JE-ZE-J06: Telephone January
    jj06 := gen_random_uuid();
    INSERT INTO acc_journal_entries (id,tenant_id,entry_number,entry_date,description,reference,entry_type,status,total_debit,total_credit,posted_at,created_at,updated_at)
    VALUES (jj06,t_id,'JE-ZE-J06','2026-01-05','Telephone & fibre — January 2026','TEL-JAN-2026','PAYMENT','POSTED',2890.00,2890.00,'2026-01-05 09:00:00','2026-01-05 09:00:00','2026-01-05 09:00:00');
    INSERT INTO acc_journal_lines (id,tenant_id,journal_entry_id,account_id,description,debit_amount,credit_amount,sort_order,created_at) VALUES
    (gen_random_uuid(),t_id,jj06,acc_telephone,'Vodacom & Vox fibre', 2890.00,   0.00,1,now()),
    (gen_random_uuid(),t_id,jj06,acc_bank,     'Debit orders',            0.00,2890.00,2,now());

    -- JE-ZE-J07: Depreciation — monthly charge (plant & equipment)
    jj07 := gen_random_uuid();
    INSERT INTO acc_journal_entries (id,tenant_id,entry_number,entry_date,description,reference,entry_type,status,total_debit,total_credit,posted_at,created_at,updated_at)
    VALUES (jj07,t_id,'JE-ZE-J07','2026-01-31','Monthly depreciation — plant & vehicles','DEP-JAN-2026','DEPRECIATION','POSTED',18500.00,18500.00,'2026-01-31 17:00:00','2026-01-31 17:00:00','2026-01-31 17:00:00');
    INSERT INTO acc_journal_lines (id,tenant_id,journal_entry_id,account_id,description,debit_amount,credit_amount,sort_order,created_at) VALUES
    (gen_random_uuid(),t_id,jj07,acc_depreciation,'Depreciation charge January',18500.00,    0.00,1,now()),
    (gen_random_uuid(),t_id,jj07,acc_bank,         'Accumulated depreciation',       0.00,18500.00,2,now());

    -- =========================================================================
    -- FEBRUARY 2026 — Still off-season: AR collected from Jan, small contract
    -- =========================================================================

    -- JE-ZE-F01: Payment received from Vereeniging Municipality (Jan AR)
    jf01 := gen_random_uuid();
    INSERT INTO acc_journal_entries (id,tenant_id,entry_number,entry_date,description,reference,entry_type,status,total_debit,total_credit,posted_at,created_at,updated_at)
    VALUES (jf01,t_id,'JE-ZE-F01','2026-02-09','Payment received — Vereeniging Municipality','RCP-ZE-J01','PAYMENT','POSTED',34500.00,34500.00,'2026-02-09 08:00:00','2026-02-09 08:00:00','2026-02-09 08:00:00');
    INSERT INTO acc_journal_lines (id,tenant_id,journal_entry_id,account_id,description,debit_amount,credit_amount,sort_order,created_at) VALUES
    (gen_random_uuid(),t_id,jf01,acc_bank,'Receipt from Vereeniging Muni',34500.00,    0.00,1,now()),
    (gen_random_uuid(),t_id,jf01,acc_ar,  'Clear debtor INV-ZE-J01',          0.00,34500.00,2,now());

    -- JE-ZE-F02: Site clearing — Heidelberg industrial development
    jf02 := gen_random_uuid();
    INSERT INTO acc_journal_entries (id,tenant_id,entry_number,entry_date,description,reference,entry_type,status,total_debit,total_credit,posted_at,created_at,updated_at)
    VALUES (jf02,t_id,'JE-ZE-F02','2026-02-14','Site clearing — Heidelberg industrial zone','INV-ZE-F01','INVOICE','POSTED',69000.00,69000.00,'2026-02-14 08:00:00','2026-02-14 08:00:00','2026-02-14 08:00:00');
    INSERT INTO acc_journal_lines (id,tenant_id,journal_entry_id,account_id,description,debit_amount,credit_amount,sort_order,created_at) VALUES
    (gen_random_uuid(),t_id,jf02,acc_bank,   'Deposit received (50%)',       34500.00,     0.00,1,now()),
    (gen_random_uuid(),t_id,jf02,acc_ar,     'Balance on completion (50%)',  34500.00,     0.00,2,now()),
    (gen_random_uuid(),t_id,jf02,acc_vat_out,'Output VAT @ 15%',                 0.00,  9000.00,3,now()),
    (gen_random_uuid(),t_id,jf02,acc_services,'Site clearing revenue',            0.00, 60000.00,4,now());

    -- JE-ZE-F03: Salaries February
    jf03 := gen_random_uuid();
    INSERT INTO acc_journal_entries (id,tenant_id,entry_number,entry_date,description,reference,entry_type,status,total_debit,total_credit,posted_at,created_at,updated_at)
    VALUES (jf03,t_id,'JE-ZE-F03','2026-02-25','Salaries — February 2026','PAYROLL-FEB-2026','PAYMENT','POSTED',42000.00,42000.00,'2026-02-25 09:00:00','2026-02-25 09:00:00','2026-02-25 09:00:00');
    INSERT INTO acc_journal_lines (id,tenant_id,journal_entry_id,account_id,description,debit_amount,credit_amount,sort_order,created_at) VALUES
    (gen_random_uuid(),t_id,jf03,acc_salaries,'Gross salaries February',42000.00,    0.00,1,now()),
    (gen_random_uuid(),t_id,jf03,acc_bank,    'Salary payments',             0.00,42000.00,2,now());

    -- JE-ZE-F04: Rent February
    jf04 := gen_random_uuid();
    INSERT INTO acc_journal_entries (id,tenant_id,entry_number,entry_date,description,reference,entry_type,status,total_debit,total_credit,posted_at,created_at,updated_at)
    VALUES (jf04,t_id,'JE-ZE-F04','2026-02-02','Office & yard rent — February 2026','RENT-FEB-2026','PAYMENT','POSTED',12000.00,12000.00,'2026-02-02 08:00:00','2026-02-02 08:00:00','2026-02-02 08:00:00');
    INSERT INTO acc_journal_lines (id,tenant_id,journal_entry_id,account_id,description,debit_amount,credit_amount,sort_order,created_at) VALUES
    (gen_random_uuid(),t_id,jf04,acc_rent,'Yard & office rent',12000.00,    0.00,1,now()),
    (gen_random_uuid(),t_id,jf04,acc_bank,'EFT to landlord',       0.00,12000.00,2,now());

    -- JE-ZE-F05: Fuel February
    jf05 := gen_random_uuid();
    INSERT INTO acc_journal_entries (id,tenant_id,entry_number,entry_date,description,reference,entry_type,status,total_debit,total_credit,posted_at,created_at,updated_at)
    VALUES (jf05,t_id,'JE-ZE-F05','2026-02-18','Diesel — Heidelberg site work','FUEL-FEB-2026','PAYMENT','POSTED',11400.00,11400.00,'2026-02-18 10:00:00','2026-02-18 10:00:00','2026-02-18 10:00:00');
    INSERT INTO acc_journal_lines (id,tenant_id,journal_entry_id,account_id,description,debit_amount,credit_amount,sort_order,created_at) VALUES
    (gen_random_uuid(),t_id,jf05,acc_fuel,'Diesel — Heidelberg contract',11400.00,    0.00,1,now()),
    (gen_random_uuid(),t_id,jf05,acc_bank,'Payment to fuel depot',           0.00,11400.00,2,now());

    -- JE-ZE-F06: Telephone February
    jf06 := gen_random_uuid();
    INSERT INTO acc_journal_entries (id,tenant_id,entry_number,entry_date,description,reference,entry_type,status,total_debit,total_credit,posted_at,created_at,updated_at)
    VALUES (jf06,t_id,'JE-ZE-F06','2026-02-05','Telephone & fibre — February 2026','TEL-FEB-2026','PAYMENT','POSTED',2890.00,2890.00,'2026-02-05 09:00:00','2026-02-05 09:00:00','2026-02-05 09:00:00');
    INSERT INTO acc_journal_lines (id,tenant_id,journal_entry_id,account_id,description,debit_amount,credit_amount,sort_order,created_at) VALUES
    (gen_random_uuid(),t_id,jf06,acc_telephone,'Vodacom & Vox fibre',2890.00,   0.00,1,now()),
    (gen_random_uuid(),t_id,jf06,acc_bank,     'Debit orders',          0.00,2890.00,2,now());

    -- JE-ZE-F07: Accounting & audit fees (annual)
    jf07 := gen_random_uuid();
    INSERT INTO acc_journal_entries (id,tenant_id,entry_number,entry_date,description,reference,entry_type,status,total_debit,total_credit,posted_at,created_at,updated_at)
    VALUES (jf07,t_id,'JE-ZE-F07','2026-02-28','Accounting & tax preparation — FY2025','ACCT-FEE-2026','PAYMENT','POSTED',15000.00,15000.00,'2026-02-28 11:00:00','2026-02-28 11:00:00','2026-02-28 11:00:00');
    INSERT INTO acc_journal_lines (id,tenant_id,journal_entry_id,account_id,description,debit_amount,credit_amount,sort_order,created_at) VALUES
    (gen_random_uuid(),t_id,jf07,acc_prof_fees,'Accounting & tax fees FY2025',15000.00,    0.00,1,now()),
    (gen_random_uuid(),t_id,jf07,acc_bank,     'EFT to auditors',                  0.00,15000.00,2,now());

    -- JE-ZE-F08: Depreciation February
    jf08 := gen_random_uuid();
    INSERT INTO acc_journal_entries (id,tenant_id,entry_number,entry_date,description,reference,entry_type,status,total_debit,total_credit,posted_at,created_at,updated_at)
    VALUES (jf08,t_id,'JE-ZE-F08','2026-02-28','Monthly depreciation — plant & vehicles','DEP-FEB-2026','DEPRECIATION','POSTED',18500.00,18500.00,'2026-02-28 17:00:00','2026-02-28 17:00:00','2026-02-28 17:00:00');
    INSERT INTO acc_journal_lines (id,tenant_id,journal_entry_id,account_id,description,debit_amount,credit_amount,sort_order,created_at) VALUES
    (gen_random_uuid(),t_id,jf08,acc_depreciation,'Depreciation charge February',18500.00,    0.00,1,now()),
    (gen_random_uuid(),t_id,jf08,acc_bank,         'Accumulated depreciation',        0.00,18500.00,2,now());

    -- =========================================================================
    -- MAY 2026 — Peak season: two large contracts, high fuel, subcontractor
    -- =========================================================================

    -- JE-ZE-M01: N14 road rehabilitation — Krugersdorp to Pretoria West Phase 1
    jm01 := gen_random_uuid();
    INSERT INTO acc_journal_entries (id,tenant_id,entry_number,entry_date,description,reference,entry_type,status,total_debit,total_credit,posted_at,created_at,updated_at)
    VALUES (jm01,t_id,'JE-ZE-M01','2026-05-02','N14 road rehabilitation — Phase 1 progress claim','INV-ZE-M01','INVOICE','POSTED',345000.00,345000.00,'2026-05-02 08:00:00','2026-05-02 08:00:00','2026-05-02 08:00:00');
    INSERT INTO acc_journal_lines (id,tenant_id,journal_entry_id,account_id,description,debit_amount,credit_amount,sort_order,created_at) VALUES
    (gen_random_uuid(),t_id,jm01,acc_bank,    'Progress payment received',    345000.00,      0.00,1,now()),
    (gen_random_uuid(),t_id,jm01,acc_vat_out, 'Output VAT @ 15%',                  0.00,  45000.00,2,now()),
    (gen_random_uuid(),t_id,jm01,acc_services,'N14 road rehab revenue',             0.00, 300000.00,3,now());

    -- JE-ZE-M02: Ekurhuleni stormwater — Phase 2
    jm02 := gen_random_uuid();
    INSERT INTO acc_journal_entries (id,tenant_id,entry_number,entry_date,description,reference,entry_type,status,total_debit,total_credit,posted_at,created_at,updated_at)
    VALUES (jm02,t_id,'JE-ZE-M02','2026-05-16','Stormwater drainage — Ekurhuleni Phase 2','INV-ZE-M02','INVOICE','POSTED',138000.00,138000.00,'2026-05-16 08:00:00','2026-05-16 08:00:00','2026-05-16 08:00:00');
    INSERT INTO acc_journal_lines (id,tenant_id,journal_entry_id,account_id,description,debit_amount,credit_amount,sort_order,created_at) VALUES
    (gen_random_uuid(),t_id,jm02,acc_ar,      'Ekurhuleni Metro — 30 day terms',138000.00,     0.00,1,now()),
    (gen_random_uuid(),t_id,jm02,acc_vat_out, 'Output VAT @ 15%',                   0.00,  18000.00,2,now()),
    (gen_random_uuid(),t_id,jm02,acc_services,'Stormwater drainage revenue',         0.00, 120000.00,3,now());

    -- JE-ZE-M03: Salaries May (bonus month — extra R8k for peak workload)
    jm03 := gen_random_uuid();
    INSERT INTO acc_journal_entries (id,tenant_id,entry_number,entry_date,description,reference,entry_type,status,total_debit,total_credit,posted_at,created_at,updated_at)
    VALUES (jm03,t_id,'JE-ZE-M03','2026-05-26','Salaries & productivity bonus — May 2026','PAYROLL-MAY-2026','PAYMENT','POSTED',50000.00,50000.00,'2026-05-26 09:00:00','2026-05-26 09:00:00','2026-05-26 09:00:00');
    INSERT INTO acc_journal_lines (id,tenant_id,journal_entry_id,account_id,description,debit_amount,credit_amount,sort_order,created_at) VALUES
    (gen_random_uuid(),t_id,jm03,acc_salaries,'Salaries + productivity bonus May',50000.00,    0.00,1,now()),
    (gen_random_uuid(),t_id,jm03,acc_bank,    'Salary payments',                      0.00,50000.00,2,now());

    -- JE-ZE-M04: Rent May
    jm04 := gen_random_uuid();
    INSERT INTO acc_journal_entries (id,tenant_id,entry_number,entry_date,description,reference,entry_type,status,total_debit,total_credit,posted_at,created_at,updated_at)
    VALUES (jm04,t_id,'JE-ZE-M04','2026-05-01','Office & yard rent — May 2026','RENT-MAY-2026','PAYMENT','POSTED',12000.00,12000.00,'2026-05-01 08:00:00','2026-05-01 08:00:00','2026-05-01 08:00:00');
    INSERT INTO acc_journal_lines (id,tenant_id,journal_entry_id,account_id,description,debit_amount,credit_amount,sort_order,created_at) VALUES
    (gen_random_uuid(),t_id,jm04,acc_rent,'Yard & office rent',12000.00,    0.00,1,now()),
    (gen_random_uuid(),t_id,jm04,acc_bank,'EFT to landlord',       0.00,12000.00,2,now());

    -- JE-ZE-M05: Fuel May (very high — two large contracts simultaneously)
    jm05 := gen_random_uuid();
    INSERT INTO acc_journal_entries (id,tenant_id,entry_number,entry_date,description,reference,entry_type,status,total_debit,total_credit,posted_at,created_at,updated_at)
    VALUES (jm05,t_id,'JE-ZE-M05','2026-05-14','Diesel — N14 & Ekurhuleni fleet May','FUEL-MAY-2026','PAYMENT','POSTED',48600.00,48600.00,'2026-05-14 10:00:00','2026-05-14 10:00:00','2026-05-14 10:00:00');
    INSERT INTO acc_journal_lines (id,tenant_id,journal_entry_id,account_id,description,debit_amount,credit_amount,sort_order,created_at) VALUES
    (gen_random_uuid(),t_id,jm05,acc_fuel,'Diesel — dual contract fleet',48600.00,    0.00,1,now()),
    (gen_random_uuid(),t_id,jm05,acc_bank,'Payment to Engen depot',           0.00,48600.00,2,now());

    -- JE-ZE-M06: Grader & compactor repair (heavy use caused early wear)
    jm06 := gen_random_uuid();
    INSERT INTO acc_journal_entries (id,tenant_id,entry_number,entry_date,description,reference,entry_type,status,total_debit,total_credit,posted_at,created_at,updated_at)
    VALUES (jm06,t_id,'JE-ZE-M06','2026-05-21','Grader & compactor service — peak workload','MAINT-MAY-2026','PAYMENT','POSTED',16800.00,16800.00,'2026-05-21 11:00:00','2026-05-21 11:00:00','2026-05-21 11:00:00');
    INSERT INTO acc_journal_lines (id,tenant_id,journal_entry_id,account_id,description,debit_amount,credit_amount,sort_order,created_at) VALUES
    (gen_random_uuid(),t_id,jm06,acc_maintenance,'Grader & compactor service',16800.00,    0.00,1,now()),
    (gen_random_uuid(),t_id,jm06,acc_bank,       'Payment to Mantis Equipment',    0.00,16800.00,2,now());

    -- JE-ZE-M07: Telephone May
    jm07 := gen_random_uuid();
    INSERT INTO acc_journal_entries (id,tenant_id,entry_number,entry_date,description,reference,entry_type,status,total_debit,total_credit,posted_at,created_at,updated_at)
    VALUES (jm07,t_id,'JE-ZE-M07','2026-05-05','Telephone & fibre — May 2026','TEL-MAY-2026','PAYMENT','POSTED',2890.00,2890.00,'2026-05-05 09:00:00','2026-05-05 09:00:00','2026-05-05 09:00:00');
    INSERT INTO acc_journal_lines (id,tenant_id,journal_entry_id,account_id,description,debit_amount,credit_amount,sort_order,created_at) VALUES
    (gen_random_uuid(),t_id,jm07,acc_telephone,'Vodacom & Vox fibre',2890.00,   0.00,1,now()),
    (gen_random_uuid(),t_id,jm07,acc_bank,     'Debit orders',          0.00,2890.00,2,now());

    -- JE-ZE-M08: Marketing — tender submission costs (N14 Phase 2 bid)
    jm08 := gen_random_uuid();
    INSERT INTO acc_journal_entries (id,tenant_id,entry_number,entry_date,description,reference,entry_type,status,total_debit,total_credit,posted_at,created_at,updated_at)
    VALUES (jm08,t_id,'JE-ZE-M08','2026-05-28','Tender document & submission costs — N14 Ph2','TENDER-MAY-2026','PAYMENT','POSTED',4500.00,4500.00,'2026-05-28 09:00:00','2026-05-28 09:00:00','2026-05-28 09:00:00');
    INSERT INTO acc_journal_lines (id,tenant_id,journal_entry_id,account_id,description,debit_amount,credit_amount,sort_order,created_at) VALUES
    (gen_random_uuid(),t_id,jm08,acc_marketing,'Tender preparation & submission',4500.00,   0.00,1,now()),
    (gen_random_uuid(),t_id,jm08,acc_bank,     'EFT — SANRAL tender portal',        0.00,4500.00,2,now());

    -- =========================================================================
    -- JUNE 2026 — Current month (partial): large contract ongoing, some DRAFT
    -- Note: JE-ZE-007..012 in V107 were incorrectly dated April — these are
    -- the correct June entries using the G (June) prefix to avoid collisions.
    -- =========================================================================

    -- JE-ZE-G01: N14 Phase 2 progress claim (contract won from May tender)
    jg01 := gen_random_uuid();
    INSERT INTO acc_journal_entries (id,tenant_id,entry_number,entry_date,description,reference,entry_type,status,total_debit,total_credit,posted_at,created_at,updated_at)
    VALUES (jg01,t_id,'JE-ZE-G01','2026-06-05','N14 road rehab Phase 2 — progress claim 1','INV-ZE-G01','INVOICE','POSTED',287500.00,287500.00,'2026-06-05 08:00:00','2026-06-05 08:00:00','2026-06-05 08:00:00');
    INSERT INTO acc_journal_lines (id,tenant_id,journal_entry_id,account_id,description,debit_amount,credit_amount,sort_order,created_at) VALUES
    (gen_random_uuid(),t_id,jg01,acc_bank,    'Progress payment received',    287500.00,      0.00,1,now()),
    (gen_random_uuid(),t_id,jg01,acc_vat_out, 'Output VAT @ 15%',                  0.00,  37500.00,2,now()),
    (gen_random_uuid(),t_id,jg01,acc_services,'N14 Phase 2 road rehab revenue',     0.00, 250000.00,3,now());

    -- JE-ZE-G02: Ekurhuleni stormwater payment received (May AR)
    jg02 := gen_random_uuid();
    INSERT INTO acc_journal_entries (id,tenant_id,entry_number,entry_date,description,reference,entry_type,status,total_debit,total_credit,posted_at,created_at,updated_at)
    VALUES (jg02,t_id,'JE-ZE-G02','2026-06-17','Payment received — Ekurhuleni Metro (May inv)','RCP-ZE-M02','PAYMENT','POSTED',138000.00,138000.00,'2026-06-17 08:00:00','2026-06-17 08:00:00','2026-06-17 08:00:00');
    INSERT INTO acc_journal_lines (id,tenant_id,journal_entry_id,account_id,description,debit_amount,credit_amount,sort_order,created_at) VALUES
    (gen_random_uuid(),t_id,jg02,acc_bank,'Receipt from Ekurhuleni Metro',138000.00,    0.00,1,now()),
    (gen_random_uuid(),t_id,jg02,acc_ar,  'Clear debtor INV-ZE-M02',           0.00,138000.00,2,now());

    -- JE-ZE-G03: Salaries June (POSTED — already paid)
    jg03 := gen_random_uuid();
    INSERT INTO acc_journal_entries (id,tenant_id,entry_number,entry_date,description,reference,entry_type,status,total_debit,total_credit,posted_at,created_at,updated_at)
    VALUES (jg03,t_id,'JE-ZE-G03','2026-06-25','Salaries — June 2026','PAYROLL-JUN-2026','PAYMENT','POSTED',42000.00,42000.00,'2026-06-25 09:00:00','2026-06-25 09:00:00','2026-06-25 09:00:00');
    INSERT INTO acc_journal_lines (id,tenant_id,journal_entry_id,account_id,description,debit_amount,credit_amount,sort_order,created_at) VALUES
    (gen_random_uuid(),t_id,jg03,acc_salaries,'Gross salaries June',42000.00,    0.00,1,now()),
    (gen_random_uuid(),t_id,jg03,acc_bank,    'Salary payments',        0.00,42000.00,2,now());

    -- JE-ZE-G04: Rent June
    jg04 := gen_random_uuid();
    INSERT INTO acc_journal_entries (id,tenant_id,entry_number,entry_date,description,reference,entry_type,status,total_debit,total_credit,posted_at,created_at,updated_at)
    VALUES (jg04,t_id,'JE-ZE-G04','2026-06-01','Office & yard rent — June 2026','RENT-JUN-2026','PAYMENT','POSTED',12000.00,12000.00,'2026-06-01 08:00:00','2026-06-01 08:00:00','2026-06-01 08:00:00');
    INSERT INTO acc_journal_lines (id,tenant_id,journal_entry_id,account_id,description,debit_amount,credit_amount,sort_order,created_at) VALUES
    (gen_random_uuid(),t_id,jg04,acc_rent,'Yard & office rent',12000.00,    0.00,1,now()),
    (gen_random_uuid(),t_id,jg04,acc_bank,'EFT to landlord',       0.00,12000.00,2,now());

    -- JE-ZE-G05: Fuel June (POSTED — first half of month)
    jg05 := gen_random_uuid();
    INSERT INTO acc_journal_entries (id,tenant_id,entry_number,entry_date,description,reference,entry_type,status,total_debit,total_credit,posted_at,created_at,updated_at)
    VALUES (jg05,t_id,'JE-ZE-G05','2026-06-13','Diesel — N14 Phase 2 fleet June','FUEL-JUN-2026','PAYMENT','POSTED',38400.00,38400.00,'2026-06-13 10:00:00','2026-06-13 10:00:00','2026-06-13 10:00:00');
    INSERT INTO acc_journal_lines (id,tenant_id,journal_entry_id,account_id,description,debit_amount,credit_amount,sort_order,created_at) VALUES
    (gen_random_uuid(),t_id,jg05,acc_fuel,'Diesel — N14 Phase 2 fleet',38400.00,    0.00,1,now()),
    (gen_random_uuid(),t_id,jg05,acc_bank,'Payment to Engen depot',        0.00,38400.00,2,now());

    -- JE-ZE-G06: Second progress claim — DRAFT (not yet submitted/approved)
    jg06 := gen_random_uuid();
    INSERT INTO acc_journal_entries (id,tenant_id,entry_number,entry_date,description,reference,entry_type,status,total_debit,total_credit,posted_at,created_at,updated_at)
    VALUES (jg06,t_id,'JE-ZE-G06','2026-06-29','N14 Phase 2 — progress claim 2 (pending approval)','INV-ZE-G02','INVOICE','DRAFT',287500.00,287500.00,NULL,'2026-06-29 08:00:00','2026-06-29 08:00:00');
    INSERT INTO acc_journal_lines (id,tenant_id,journal_entry_id,account_id,description,debit_amount,credit_amount,sort_order,created_at) VALUES
    (gen_random_uuid(),t_id,jg06,acc_ar,      'SANRAL — claim 2 pending approval',287500.00,      0.00,1,now()),
    (gen_random_uuid(),t_id,jg06,acc_vat_out, 'Output VAT @ 15%',                      0.00,  37500.00,2,now()),
    (gen_random_uuid(),t_id,jg06,acc_services,'N14 Phase 2 road rehab revenue',         0.00, 250000.00,3,now());

END $$;

COMMIT;

-- VERIFY:
-- SELECT entry_number, entry_date, description, entry_type, status, total_debit
--   FROM acc_journal_entries
--   WHERE tenant_id='9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f'
--   ORDER BY entry_date, entry_number;