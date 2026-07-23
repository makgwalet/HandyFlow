-- ═══════════════════════════════════════════════════════════════════
-- Links Capitec Bank, Zeta Earthmoving Cheque, and Business Savings to
-- distinct new GL accounts (not sharing 1020 with Business Cheque),
-- and seeds a small, real transaction history for each — same
-- treatment as Business Cheque Account got. Run manually via psql.
-- ═══════════════════════════════════════════════════════════════════

BEGIN;

-- New GL accounts (custom, is_system=false)
INSERT INTO acc_accounts (id, tenant_id, account_code, account_name, account_type, account_subtype, parent_id, is_system, active, opening_balance, description, created_at, updated_at)
VALUES
    ('3de062ba-3122-4f36-979f-ebe03af6c52a', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', '1021', 'Bank — Capitec Cheque Account', 'ASSET', 'BANK', NULL, false, true, 0, NULL, now(), now()),
    ('a37632fe-f9f3-41dc-9aae-be633fec5f56', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', '1022', 'Bank — Zeta Earthmoving Cheque Account', 'ASSET', 'BANK', NULL, false, true, 0, NULL, now(), now());

-- Link all three bank accounts to their GL accounts
UPDATE acc_bank_accounts SET account_id = '3de062ba-3122-4f36-979f-ebe03af6c52a', updated_at = now() WHERE id = '47a89bba-b841-459d-b337-cb647043bae1';
UPDATE acc_bank_accounts SET account_id = 'a37632fe-f9f3-41dc-9aae-be633fec5f56', updated_at = now() WHERE id = '46042315-776f-438d-a25d-8602d0779861';
UPDATE acc_bank_accounts SET account_id = '6db25601-8651-4e39-8ca0-1b6d85ae11d3', updated_at = now() WHERE id = '598cb33a-606f-4352-9bbb-e1b1b0225998';

-- JE-2026-00020: Office supplies purchase (CAPITEC)
INSERT INTO acc_journal_entries
    (id, tenant_id, entry_number, entry_date, description, reference, entry_type, status, total_debit, total_credit, posted_at, created_by, created_at, updated_at, version)
VALUES
    ('9ec60a1b-3527-4cfc-93ec-c8e219b86c7b', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'JE-2026-00020', '2026-07-10', 'Office supplies purchase', 'OFFICE-JUL-CAP', 'MANUAL', 'POSTED', 1850, 1850, '2026-07-10T09:00:00Z', NULL, '2026-07-10T09:00:00Z', '2026-07-10T09:00:00Z', 0);
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('93dca0b6-8764-4c6f-89b0-6eb5a3710c90', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', '9ec60a1b-3527-4cfc-93ec-c8e219b86c7b', 'f295e392-4d90-4ec7-89ff-ac66ab4ce75b', 'Stationery & supplies', 1850, 0, 0, '2026-07-10T09:00:00Z');
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('1715bc28-426e-489d-9f2b-87f0ad95889e', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', '9ec60a1b-3527-4cfc-93ec-c8e219b86c7b', '3de062ba-3122-4f36-979f-ebe03af6c52a', 'Paid from Capitec', 0, 1850, 1, '2026-07-10T09:00:00Z');

INSERT INTO acc_bank_transactions
    (id, tenant_id, bank_account_id, transaction_date, description, reference, amount, transaction_type, balance_after, reconciled, reconciled_at, journal_line_id, created_at, updated_at)
VALUES
    ('5c24f5b8-21e2-408a-84d3-c0bb58ba2f6a', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', '47a89bba-b841-459d-b337-cb647043bae1', '2026-07-10', 'Office supplies purchase', 'OFFICE-JUL-CAP', 1850, 'DEBIT', 602379.3, false, NULL, NULL, '2026-07-10T10:00:00Z', '2026-07-10T10:00:00Z');
-- JE-2026-00021: Consulting fee received (CAPITEC)
INSERT INTO acc_journal_entries
    (id, tenant_id, entry_number, entry_date, description, reference, entry_type, status, total_debit, total_credit, posted_at, created_by, created_at, updated_at, version)
VALUES
    ('7d71fcfd-bf7e-4316-bdf0-192ddbe458e1', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'JE-2026-00021', '2026-07-15', 'Consulting fee received', 'CONS-JUL-CAP', 'MANUAL', 'POSTED', 23000, 23000, '2026-07-15T09:00:00Z', NULL, '2026-07-15T09:00:00Z', '2026-07-15T09:00:00Z', 0);
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('f3d4a642-e243-4b8e-a657-534959ac8c80', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', '7d71fcfd-bf7e-4316-bdf0-192ddbe458e1', '3de062ba-3122-4f36-979f-ebe03af6c52a', 'Receipt — consulting', 23000, 0, 0, '2026-07-15T09:00:00Z');
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('85d7542b-c9b7-492e-ad42-6439da2aaa44', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', '7d71fcfd-bf7e-4316-bdf0-192ddbe458e1', '9d5ecd18-0fce-4e6f-898a-7098bac2aeec', 'Output VAT @ 15%', 0, 3000, 1, '2026-07-15T09:00:00Z');
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('ba194484-c0df-48f4-a3f3-79913afad6ad', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', '7d71fcfd-bf7e-4316-bdf0-192ddbe458e1', '02c4d1e4-d663-4082-8361-506c8f2b6e4f', 'Consulting revenue', 0, 20000, 2, '2026-07-15T09:00:00Z');

INSERT INTO acc_bank_transactions
    (id, tenant_id, bank_account_id, transaction_date, description, reference, amount, transaction_type, balance_after, reconciled, reconciled_at, journal_line_id, created_at, updated_at)
VALUES
    ('5fcd77be-58f9-45ba-a316-1eb9e4d0eb6d', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', '47a89bba-b841-459d-b337-cb647043bae1', '2026-07-15', 'Consulting fee received', 'CONS-JUL-CAP', 23000, 'CREDIT', 625379.3, false, NULL, NULL, '2026-07-15T10:00:00Z', '2026-07-15T10:00:00Z');
INSERT INTO acc_bank_transactions
    (id, tenant_id, bank_account_id, transaction_date, description, reference, amount, transaction_type, balance_after, reconciled, reconciled_at, journal_line_id, created_at, updated_at)
VALUES
    ('66058564-7cda-493b-b5af-ab066a1831ff', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', '47a89bba-b841-459d-b337-cb647043bae1', '2026-07-20', 'Bank charges - monthly fee', 'BANKFEE-JUL-CAP', 95.0, 'DEBIT', 625284.3, false, NULL, NULL, '2026-07-20T10:00:00Z', '2026-07-20T10:00:00Z');
-- CAPITEC final balance: 625284.30

-- JE-2026-00022: Consulting fee received (ZETA)
INSERT INTO acc_journal_entries
    (id, tenant_id, entry_number, entry_date, description, reference, entry_type, status, total_debit, total_credit, posted_at, created_by, created_at, updated_at, version)
VALUES
    ('d37be398-52f1-41b9-a106-0a06dbe06ed3', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'JE-2026-00022', '2026-07-12', 'Consulting fee received', 'CONS-JUL-ZETA', 'MANUAL', 'POSTED', 2300, 2300, '2026-07-12T09:00:00Z', NULL, '2026-07-12T09:00:00Z', '2026-07-12T09:00:00Z', 0);
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('4ed78d41-d464-4616-92db-6a3372908c51', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'd37be398-52f1-41b9-a106-0a06dbe06ed3', 'a37632fe-f9f3-41dc-9aae-be633fec5f56', 'Receipt — consulting', 2300, 0, 0, '2026-07-12T09:00:00Z');
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('d309ae81-401f-4373-93c3-e286d5e19580', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'd37be398-52f1-41b9-a106-0a06dbe06ed3', '9d5ecd18-0fce-4e6f-898a-7098bac2aeec', 'Output VAT @ 15%', 0, 300, 1, '2026-07-12T09:00:00Z');
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('187bcf7f-ba0c-4faa-b221-dbe562bbd86b', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'd37be398-52f1-41b9-a106-0a06dbe06ed3', '02c4d1e4-d663-4082-8361-506c8f2b6e4f', 'Consulting revenue', 0, 2000, 2, '2026-07-12T09:00:00Z');

INSERT INTO acc_bank_transactions
    (id, tenant_id, bank_account_id, transaction_date, description, reference, amount, transaction_type, balance_after, reconciled, reconciled_at, journal_line_id, created_at, updated_at)
VALUES
    ('c7c1a0e8-0cd9-473d-abc1-0a2868e97718', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', '46042315-776f-438d-a25d-8602d0779861', '2026-07-12', 'Consulting fee received', 'CONS-JUL-ZETA', 2300, 'CREDIT', 7300.0, false, NULL, NULL, '2026-07-12T10:00:00Z', '2026-07-12T10:00:00Z');
INSERT INTO acc_bank_transactions
    (id, tenant_id, bank_account_id, transaction_date, description, reference, amount, transaction_type, balance_after, reconciled, reconciled_at, journal_line_id, created_at, updated_at)
VALUES
    ('9428e061-a22d-4888-be9d-ac34c25de3f6', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', '46042315-776f-438d-a25d-8602d0779861', '2026-07-18', 'Bank charges - monthly fee', 'BANKFEE-JUL-ZETA', 45.0, 'DEBIT', 7255.0, false, NULL, NULL, '2026-07-18T10:00:00Z', '2026-07-18T10:00:00Z');
-- ZETA final balance: 7255.00

-- JE-2026-00023: Interest received - savings (SAVINGS)
INSERT INTO acc_journal_entries
    (id, tenant_id, entry_number, entry_date, description, reference, entry_type, status, total_debit, total_credit, posted_at, created_by, created_at, updated_at, version)
VALUES
    ('edfa1184-2a8d-45c7-8745-f2d39978fdb2', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'JE-2026-00023', '2026-07-15', 'Interest received - savings', 'INT-JUL-SAV', 'MANUAL', 'POSTED', 28.5, 28.5, '2026-07-15T09:00:00Z', NULL, '2026-07-15T09:00:00Z', '2026-07-15T09:00:00Z', 0);
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('6a04ac41-6983-4732-ac87-495561c8100e', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'edfa1184-2a8d-45c7-8745-f2d39978fdb2', '6db25601-8651-4e39-8ca0-1b6d85ae11d3', 'Interest earned', 28.5, 0, 0, '2026-07-15T09:00:00Z');
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('2f817f4a-a435-4000-9d76-4ddc95891810', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'edfa1184-2a8d-45c7-8745-f2d39978fdb2', '7aa8b68b-c171-4c41-8275-79d2bbda7476', 'Interest income', 0, 28.5, 1, '2026-07-15T09:00:00Z');

INSERT INTO acc_bank_transactions
    (id, tenant_id, bank_account_id, transaction_date, description, reference, amount, transaction_type, balance_after, reconciled, reconciled_at, journal_line_id, created_at, updated_at)
VALUES
    ('45d42567-a296-4126-9c00-a7537fc7a89a', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', '598cb33a-606f-4352-9bbb-e1b1b0225998', '2026-07-15', 'Interest received - savings', 'INT-JUL-SAV', 28.5, 'CREDIT', 5340.5, false, NULL, NULL, '2026-07-15T10:00:00Z', '2026-07-15T10:00:00Z');
INSERT INTO acc_bank_transactions
    (id, tenant_id, bank_account_id, transaction_date, description, reference, amount, transaction_type, balance_after, reconciled, reconciled_at, journal_line_id, created_at, updated_at)
VALUES
    ('9db65eea-b639-402b-a2f6-50fbad14355d', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', '598cb33a-606f-4352-9bbb-e1b1b0225998', '2026-07-25', 'Bank charges - monthly fee', 'BANKFEE-JUL-SAV', 15.0, 'DEBIT', 5325.5, false, NULL, NULL, '2026-07-25T10:00:00Z', '2026-07-25T10:00:00Z');
-- SAVINGS final balance: 5325.50

-- Update each account's current_balance to match its final running balance
UPDATE acc_bank_accounts SET current_balance = 625284.30 WHERE id = '47a89bba-b841-459d-b337-cb647043bae1';
UPDATE acc_bank_accounts SET current_balance = 7255.00 WHERE id = '46042315-776f-438d-a25d-8602d0779861';
UPDATE acc_bank_accounts SET current_balance = 5325.50 WHERE id = '598cb33a-606f-4352-9bbb-e1b1b0225998';

-- Resync the journal sequence again (new entries added above)
INSERT INTO acc_journal_sequences (tenant_id, year, last_seq)
SELECT
    tenant_id,
    CAST(SUBSTRING(entry_number FROM 'JE-(\d{4})-') AS INT) AS year,
    MAX(CAST(SUBSTRING(entry_number FROM 'JE-\d{4}-(\d+)') AS INT)) AS max_seq
FROM acc_journal_entries
WHERE tenant_id = '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f' AND entry_number ~ '^JE-\d{4}-\d+$'
GROUP BY tenant_id, CAST(SUBSTRING(entry_number FROM 'JE-(\d{4})-') AS INT)
ON CONFLICT (tenant_id, year)
DO UPDATE SET last_seq = GREATEST(acc_journal_sequences.last_seq, EXCLUDED.last_seq);

-- Sanity check before committing
SELECT ba.account_name, ba.current_balance, a.account_code, a.account_name AS gl_name
FROM acc_bank_accounts ba LEFT JOIN acc_accounts a ON a.id = ba.account_id
WHERE ba.tenant_id = '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f' ORDER BY ba.bank_name;

COMMIT;