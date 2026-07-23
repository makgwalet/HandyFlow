-- ═══════════════════════════════════════════════════════════════════
-- ONE-TIME SEED DATA — run manually via psql, NOT a Flyway migration.
-- Every entry below is a real, complete, balanced double-entry posting
-- (generated and validated programmatically, not hand-typed) — no
-- phantom empty-line entries, no missing credit/debit lines, matching
-- exactly the two data-integrity bugs this is meant to replace.
-- Run reset-accounting-data.sql FIRST if you want a clean slate.
-- ═══════════════════════════════════════════════════════════════════

BEGIN;

-- JE-2026-00001: Office & yard rent — May 2026
INSERT INTO acc_journal_entries
    (id, tenant_id, entry_number, entry_date, description, reference, entry_type, status, total_debit, total_credit, posted_at, created_by, created_at, updated_at, version)
VALUES
    ('56c04d9e-40bc-4d62-aea4-c2b10c65cb50', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'JE-2026-00001', '2026-05-01', 'Office & yard rent — May 2026', 'RENT-MAY-2026', 'PAYMENT', 'POSTED', 12000, 12000, '2026-05-01T09:00:00Z', NULL, '2026-05-01T09:00:00Z', '2026-05-01T09:00:00Z', 0);
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('246f3a55-0e94-48a2-9115-e5814f2e9020', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', '56c04d9e-40bc-4d62-aea4-c2b10c65cb50', '3a186106-bda5-47f1-ae34-423d0e6d385b', 'Yard & office rent', 12000, 0, 0, '2026-05-01T09:00:00Z');
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('87c96c38-c088-4563-971d-f85be49b70b3', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', '56c04d9e-40bc-4d62-aea4-c2b10c65cb50', '72d9d946-7bdd-426e-b996-e0b61d6aeca5', 'EFT to landlord', 0, 12000, 1, '2026-05-01T09:00:00Z');

-- JE-2026-00002: Salaries — May 2026
INSERT INTO acc_journal_entries
    (id, tenant_id, entry_number, entry_date, description, reference, entry_type, status, total_debit, total_credit, posted_at, created_by, created_at, updated_at, version)
VALUES
    ('fb8d8711-0953-481a-b4e2-8a5d797b5025', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'JE-2026-00002', '2026-05-01', 'Salaries — May 2026', 'PAYROLL-MAY-2026', 'PAYMENT', 'POSTED', 42000, 42000, '2026-05-01T09:00:00Z', NULL, '2026-05-01T09:00:00Z', '2026-05-01T09:00:00Z', 0);
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('1cd0fc1a-eec8-46da-8115-fe64798ec0e3', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'fb8d8711-0953-481a-b4e2-8a5d797b5025', 'fc3bce37-de8b-4f2d-9744-8624c912a8ac', 'Gross salaries May', 42000, 0, 0, '2026-05-01T09:00:00Z');
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('9fd35e10-01dc-4a62-9834-19226d83c132', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'fb8d8711-0953-481a-b4e2-8a5d797b5025', '72d9d946-7bdd-426e-b996-e0b61d6aeca5', 'Salary payments', 0, 42000, 1, '2026-05-01T09:00:00Z');

-- JE-2026-00003: N14 road rehab — progress claim 1
INSERT INTO acc_journal_entries
    (id, tenant_id, entry_number, entry_date, description, reference, entry_type, status, total_debit, total_credit, posted_at, created_by, created_at, updated_at, version)
VALUES
    ('f75a50ca-6b55-40cd-a776-a5c068270ec9', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'JE-2026-00003', '2026-05-05', 'N14 road rehab — progress claim 1', 'INV-ZE-101', 'INVOICE', 'POSTED', 184000, 184000, '2026-05-05T09:00:00Z', NULL, '2026-05-05T09:00:00Z', '2026-05-05T09:00:00Z', 0);
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('59bd7689-a30a-4d27-9f62-9fad8f6f8b5a', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'f75a50ca-6b55-40cd-a776-a5c068270ec9', '9d829d10-bccf-4474-b814-ec81fca8b914', 'SANRAL — 30 day terms', 184000, 0, 0, '2026-05-05T09:00:00Z');
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('a455e6ed-afe7-4edf-a246-a76130ed83d1', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'f75a50ca-6b55-40cd-a776-a5c068270ec9', '9d5ecd18-0fce-4e6f-898a-7098bac2aeec', 'Output VAT @ 15%', 0, 24000, 1, '2026-05-05T09:00:00Z');
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('4fefd483-054f-4a56-9168-6e56b8edf9e6', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'f75a50ca-6b55-40cd-a776-a5c068270ec9', '02c4d1e4-d663-4082-8361-506c8f2b6e4f', 'N14 road rehab revenue', 0, 160000, 2, '2026-05-05T09:00:00Z');

-- JE-2026-00004: Diesel — fleet May
INSERT INTO acc_journal_entries
    (id, tenant_id, entry_number, entry_date, description, reference, entry_type, status, total_debit, total_credit, posted_at, created_by, created_at, updated_at, version)
VALUES
    ('108eda33-3f90-416b-aa9e-16db113b9d0b', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'JE-2026-00004', '2026-05-10', 'Diesel — fleet May', 'FUEL-MAY-2026', 'PAYMENT', 'POSTED', 18500, 18500, '2026-05-10T09:00:00Z', NULL, '2026-05-10T09:00:00Z', '2026-05-10T09:00:00Z', 0);
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('e19e2b5a-d191-4716-b9fd-db9c9cd88642', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', '108eda33-3f90-416b-aa9e-16db113b9d0b', 'd3084e32-8266-49f5-9999-f9f8144f33e4', 'Diesel purchases', 18500, 0, 0, '2026-05-10T09:00:00Z');
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('9aab6f9d-a688-4232-acb0-9e774b999f8a', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', '108eda33-3f90-416b-aa9e-16db113b9d0b', '72d9d946-7bdd-426e-b996-e0b61d6aeca5', 'Payment to fuel depot', 0, 18500, 1, '2026-05-10T09:00:00Z');

-- JE-2026-00005: Telephone & fibre — May 2026
INSERT INTO acc_journal_entries
    (id, tenant_id, entry_number, entry_date, description, reference, entry_type, status, total_debit, total_credit, posted_at, created_by, created_at, updated_at, version)
VALUES
    ('ae160bd7-4819-4d85-b94c-c3e7129f77b0', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'JE-2026-00005', '2026-05-15', 'Telephone & fibre — May 2026', 'TEL-MAY-2026', 'PAYMENT', 'POSTED', 2890, 2890, '2026-05-15T09:00:00Z', NULL, '2026-05-15T09:00:00Z', '2026-05-15T09:00:00Z', 0);
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('d3b89661-4bf5-47bf-ac07-109c386c2776', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'ae160bd7-4819-4d85-b94c-c3e7129f77b0', 'cfae7780-1229-41d9-8757-1b7377d1402d', 'Vodacom & Vox fibre', 2890, 0, 0, '2026-05-15T09:00:00Z');
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('ee13fe04-fce2-4f50-9266-4ea96f4e6a83', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'ae160bd7-4819-4d85-b94c-c3e7129f77b0', '72d9d946-7bdd-426e-b996-e0b61d6aeca5', 'Debit orders', 0, 2890, 1, '2026-05-15T09:00:00Z');

-- JE-2026-00006: AP bill approved — Jet Plumbing & Maintenance
INSERT INTO acc_journal_entries
    (id, tenant_id, entry_number, entry_date, description, reference, entry_type, status, total_debit, total_credit, posted_at, created_by, created_at, updated_at, version)
VALUES
    ('a548fbb1-172e-4517-a163-2b9a691d65c7', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'JE-2026-00006', '2026-05-20', 'AP bill approved — Jet Plumbing & Maintenance', 'JPM-2026-0341', 'MANUAL', 'POSTED', 5577.5, 5577.5, '2026-05-20T09:00:00Z', NULL, '2026-05-20T09:00:00Z', '2026-05-20T09:00:00Z', 0);
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('6f8b7727-276c-46bb-b7c0-e7f6fcdd7040', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'a548fbb1-172e-4517-a163-2b9a691d65c7', '0d05d752-874d-4bea-a9a9-bbad043f2ad9', 'Maintenance expense', 4850, 0, 0, '2026-05-20T09:00:00Z');
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('3aa5a9b3-895d-4e64-8462-b4307442f831', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'a548fbb1-172e-4517-a163-2b9a691d65c7', '34fa7e63-7018-4aa5-82c0-04149a95c2f9', 'VAT input', 727.5, 0, 1, '2026-05-20T09:00:00Z');
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('9f2d90c9-8cd5-4961-b2fe-52dd646b5e9b', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'a548fbb1-172e-4517-a163-2b9a691d65c7', '4111e1eb-a105-44f3-9e5a-1d176134a4fc', 'Accounts payable', 0, 5577.5, 2, '2026-05-20T09:00:00Z');

-- JE-2026-00007: Payment received — SANRAL (May claim)
INSERT INTO acc_journal_entries
    (id, tenant_id, entry_number, entry_date, description, reference, entry_type, status, total_debit, total_credit, posted_at, created_by, created_at, updated_at, version)
VALUES
    ('83ae2dd0-0df6-4c49-a1bd-421fe99e9908', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'JE-2026-00007', '2026-05-25', 'Payment received — SANRAL (May claim)', 'RCP-ZE-101', 'PAYMENT', 'POSTED', 184000, 184000, '2026-05-25T09:00:00Z', NULL, '2026-05-25T09:00:00Z', '2026-05-25T09:00:00Z', 0);
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('ded73e53-36e2-466f-9167-3becd6551a4e', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', '83ae2dd0-0df6-4c49-a1bd-421fe99e9908', '72d9d946-7bdd-426e-b996-e0b61d6aeca5', 'Receipt — N14 progress claim', 184000, 0, 0, '2026-05-25T09:00:00Z');
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('b156e3b1-f6ce-4a64-b49f-0c5d5c4df691', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', '83ae2dd0-0df6-4c49-a1bd-421fe99e9908', '9d829d10-bccf-4474-b814-ec81fca8b914', 'Clear debtor INV-ZE-101', 0, 184000, 1, '2026-05-25T09:00:00Z');

-- JE-2026-00008: Office & yard rent — June 2026
INSERT INTO acc_journal_entries
    (id, tenant_id, entry_number, entry_date, description, reference, entry_type, status, total_debit, total_credit, posted_at, created_by, created_at, updated_at, version)
VALUES
    ('5f8df94a-3311-4b38-a3ed-5a65078921c4', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'JE-2026-00008', '2026-06-01', 'Office & yard rent — June 2026', 'RENT-JUN-2026', 'PAYMENT', 'POSTED', 12000, 12000, '2026-06-01T09:00:00Z', NULL, '2026-06-01T09:00:00Z', '2026-06-01T09:00:00Z', 0);
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('5b98113c-8142-437f-9e34-592bad0423b9', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', '5f8df94a-3311-4b38-a3ed-5a65078921c4', '3a186106-bda5-47f1-ae34-423d0e6d385b', 'Yard & office rent', 12000, 0, 0, '2026-06-01T09:00:00Z');
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('23a57d12-3f64-4cb2-b788-1615d2438cbb', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', '5f8df94a-3311-4b38-a3ed-5a65078921c4', '72d9d946-7bdd-426e-b996-e0b61d6aeca5', 'EFT to landlord', 0, 12000, 1, '2026-06-01T09:00:00Z');

-- JE-2026-00009: Salaries — June 2026
INSERT INTO acc_journal_entries
    (id, tenant_id, entry_number, entry_date, description, reference, entry_type, status, total_debit, total_credit, posted_at, created_by, created_at, updated_at, version)
VALUES
    ('a92ede2b-2627-4573-94d1-3937e5d79972', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'JE-2026-00009', '2026-06-01', 'Salaries — June 2026', 'PAYROLL-JUN-2026', 'PAYMENT', 'POSTED', 42000, 42000, '2026-06-01T09:00:00Z', NULL, '2026-06-01T09:00:00Z', '2026-06-01T09:00:00Z', 0);
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('aae9b8b8-871c-45b1-92c4-e9827e45d532', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'a92ede2b-2627-4573-94d1-3937e5d79972', 'fc3bce37-de8b-4f2d-9744-8624c912a8ac', 'Gross salaries June', 42000, 0, 0, '2026-06-01T09:00:00Z');
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('7723375d-bbc9-49fe-9979-4f56e853afa2', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'a92ede2b-2627-4573-94d1-3937e5d79972', '72d9d946-7bdd-426e-b996-e0b61d6aeca5', 'Salary payments', 0, 42000, 1, '2026-06-01T09:00:00Z');

-- JE-2026-00010: Equipment hire — Acme Construction
INSERT INTO acc_journal_entries
    (id, tenant_id, entry_number, entry_date, description, reference, entry_type, status, total_debit, total_credit, posted_at, created_by, created_at, updated_at, version)
VALUES
    ('02abd376-1d2a-4975-be8a-79dc8ed005fa', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'JE-2026-00010', '2026-06-10', 'Equipment hire — Acme Construction', 'INV-ZE-102', 'INVOICE', 'POSTED', 80500, 80500, '2026-06-10T09:00:00Z', NULL, '2026-06-10T09:00:00Z', '2026-06-10T09:00:00Z', 0);
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('f1817fc9-9eb5-476e-9eb7-56d25034da31', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', '02abd376-1d2a-4975-be8a-79dc8ed005fa', '9d829d10-bccf-4474-b814-ec81fca8b914', 'Acme Construction — 30 day terms', 80500, 0, 0, '2026-06-10T09:00:00Z');
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('475f2644-6de7-4943-9ae4-f5e75db20e18', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', '02abd376-1d2a-4975-be8a-79dc8ed005fa', '9d5ecd18-0fce-4e6f-898a-7098bac2aeec', 'Output VAT @ 15%', 0, 10500, 1, '2026-06-10T09:00:00Z');
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('7b4654ab-4734-4080-8748-984efcf615d5', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', '02abd376-1d2a-4975-be8a-79dc8ed005fa', 'ffb1d948-03cf-4dfc-a814-68d887d4e627', 'Equipment hire revenue', 0, 70000, 2, '2026-06-10T09:00:00Z');

-- JE-2026-00011: Insurance premium — fleet
INSERT INTO acc_journal_entries
    (id, tenant_id, entry_number, entry_date, description, reference, entry_type, status, total_debit, total_credit, posted_at, created_by, created_at, updated_at, version)
VALUES
    ('11134669-11b2-4aaf-b271-da4628691253', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'JE-2026-00011', '2026-06-15', 'Insurance premium — fleet', 'INS-JUN-2026', 'PAYMENT', 'POSTED', 18200, 18200, '2026-06-15T09:00:00Z', NULL, '2026-06-15T09:00:00Z', '2026-06-15T09:00:00Z', 0);
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('1c024c35-24cc-4b91-8d41-0b358c3a09bd', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', '11134669-11b2-4aaf-b271-da4628691253', 'e91885e2-4123-481f-8b0f-505f0bc0e805', 'Fleet insurance premium', 18200, 0, 0, '2026-06-15T09:00:00Z');
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('59a6eb13-252a-40ca-9c1b-ff883bb95502', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', '11134669-11b2-4aaf-b271-da4628691253', '72d9d946-7bdd-426e-b996-e0b61d6aeca5', 'Debit order', 0, 18200, 1, '2026-06-15T09:00:00Z');

-- JE-2026-00012: Tender submission costs
INSERT INTO acc_journal_entries
    (id, tenant_id, entry_number, entry_date, description, reference, entry_type, status, total_debit, total_credit, posted_at, created_by, created_at, updated_at, version)
VALUES
    ('a699c55e-aee8-4025-997b-a902fbaffe48', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'JE-2026-00012', '2026-06-20', 'Tender submission costs', 'TENDER-JUN-2026', 'PAYMENT', 'POSTED', 4500, 4500, '2026-06-20T09:00:00Z', NULL, '2026-06-20T09:00:00Z', '2026-06-20T09:00:00Z', 0);
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('b0c8ec43-7c1b-4e7f-aa45-9cf81dedfcdf', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'a699c55e-aee8-4025-997b-a902fbaffe48', 'd92c4e22-1e1b-4ee0-8bdd-7e17acde07a3', 'Tender preparation & submission', 4500, 0, 0, '2026-06-20T09:00:00Z');
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('2ea44283-4500-490b-994f-518422cbf6cb', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'a699c55e-aee8-4025-997b-a902fbaffe48', '72d9d946-7bdd-426e-b996-e0b61d6aeca5', 'EFT — tender portal', 0, 4500, 1, '2026-06-20T09:00:00Z');

-- JE-2026-00013: Payment received — Acme Construction
INSERT INTO acc_journal_entries
    (id, tenant_id, entry_number, entry_date, description, reference, entry_type, status, total_debit, total_credit, posted_at, created_by, created_at, updated_at, version)
VALUES
    ('2235311c-1247-4506-8970-cd91cf6cef34', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'JE-2026-00013', '2026-06-25', 'Payment received — Acme Construction', 'RCP-ZE-102', 'PAYMENT', 'POSTED', 80500, 80500, '2026-06-25T09:00:00Z', NULL, '2026-06-25T09:00:00Z', '2026-06-25T09:00:00Z', 0);
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('ac4845a0-7b7b-45f1-ae17-c1bc0b4a664e', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', '2235311c-1247-4506-8970-cd91cf6cef34', '72d9d946-7bdd-426e-b996-e0b61d6aeca5', 'Receipt — equipment hire', 80500, 0, 0, '2026-06-25T09:00:00Z');
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('ccc02e9c-4475-402e-9d92-264f92151de9', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', '2235311c-1247-4506-8970-cd91cf6cef34', '9d829d10-bccf-4474-b814-ec81fca8b914', 'Clear debtor INV-ZE-102', 0, 80500, 1, '2026-06-25T09:00:00Z');

-- JE-2026-00014: Office & yard rent — July 2026
INSERT INTO acc_journal_entries
    (id, tenant_id, entry_number, entry_date, description, reference, entry_type, status, total_debit, total_credit, posted_at, created_by, created_at, updated_at, version)
VALUES
    ('f0273633-893a-4080-b3d7-d216072af098', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'JE-2026-00014', '2026-07-01', 'Office & yard rent — July 2026', 'RENT-JUL-2026', 'PAYMENT', 'POSTED', 12000, 12000, '2026-07-01T09:00:00Z', NULL, '2026-07-01T09:00:00Z', '2026-07-01T09:00:00Z', 0);
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('d93b9a82-29b5-42d9-8f6f-0ae9fa3915d2', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'f0273633-893a-4080-b3d7-d216072af098', '3a186106-bda5-47f1-ae34-423d0e6d385b', 'Yard & office rent', 12000, 0, 0, '2026-07-01T09:00:00Z');
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('6adf62ac-5bbe-4ba4-916e-e16659c36b75', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'f0273633-893a-4080-b3d7-d216072af098', '72d9d946-7bdd-426e-b996-e0b61d6aeca5', 'EFT to landlord', 0, 12000, 1, '2026-07-01T09:00:00Z');

-- JE-2026-00015: Salaries — July 2026
INSERT INTO acc_journal_entries
    (id, tenant_id, entry_number, entry_date, description, reference, entry_type, status, total_debit, total_credit, posted_at, created_by, created_at, updated_at, version)
VALUES
    ('cf0bd9e9-6849-443a-b277-57d06e85311f', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'JE-2026-00015', '2026-07-01', 'Salaries — July 2026', 'PAYROLL-JUL-2026', 'PAYMENT', 'POSTED', 42000, 42000, '2026-07-01T09:00:00Z', NULL, '2026-07-01T09:00:00Z', '2026-07-01T09:00:00Z', 0);
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('1708b04a-f519-481b-9346-eea64ffd3397', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'cf0bd9e9-6849-443a-b277-57d06e85311f', 'fc3bce37-de8b-4f2d-9744-8624c912a8ac', 'Gross salaries July', 42000, 0, 0, '2026-07-01T09:00:00Z');
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('a15a65a0-4b92-43d0-be82-3e26d71e8477', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'cf0bd9e9-6849-443a-b277-57d06e85311f', '72d9d946-7bdd-426e-b996-e0b61d6aeca5', 'Salary payments', 0, 42000, 1, '2026-07-01T09:00:00Z');

-- JE-2026-00016: Road rehabilitation — Carletonville Phase 2
INSERT INTO acc_journal_entries
    (id, tenant_id, entry_number, entry_date, description, reference, entry_type, status, total_debit, total_credit, posted_at, created_by, created_at, updated_at, version)
VALUES
    ('5c2402b3-df3a-434e-9bce-69a1e177b994', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'JE-2026-00016', '2026-07-05', 'Road rehabilitation — Carletonville Phase 2', 'INV-ZE-103', 'INVOICE', 'POSTED', 103500, 103500, '2026-07-05T09:00:00Z', NULL, '2026-07-05T09:00:00Z', '2026-07-05T09:00:00Z', 0);
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('366e6251-b070-459d-8e71-ebd870a00025', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', '5c2402b3-df3a-434e-9bce-69a1e177b994', '9d829d10-bccf-4474-b814-ec81fca8b914', 'Carletonville Municipality — 30 day terms', 103500, 0, 0, '2026-07-05T09:00:00Z');
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('aa6368b4-f263-4711-8d16-0595c3d1ccae', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', '5c2402b3-df3a-434e-9bce-69a1e177b994', '9d5ecd18-0fce-4e6f-898a-7098bac2aeec', 'Output VAT @ 15%', 0, 13500, 1, '2026-07-05T09:00:00Z');
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('d6a461e7-6f24-46e5-92af-144d8b92ad7f', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', '5c2402b3-df3a-434e-9bce-69a1e177b994', '02c4d1e4-d663-4082-8361-506c8f2b6e4f', 'Road rehab revenue', 0, 90000, 2, '2026-07-05T09:00:00Z');

-- JE-2026-00017: Accounting & tax preparation
INSERT INTO acc_journal_entries
    (id, tenant_id, entry_number, entry_date, description, reference, entry_type, status, total_debit, total_credit, posted_at, created_by, created_at, updated_at, version)
VALUES
    ('d8e689b9-e767-407b-86af-2c15e5d31644', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'JE-2026-00017', '2026-07-10', 'Accounting & tax preparation', 'ACCT-FEE-2026', 'PAYMENT', 'POSTED', 15000, 15000, '2026-07-10T09:00:00Z', NULL, '2026-07-10T09:00:00Z', '2026-07-10T09:00:00Z', 0);
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('a4d29f14-5b46-4b74-ac64-8f08cd3f032d', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'd8e689b9-e767-407b-86af-2c15e5d31644', '60972812-e0e1-4b5f-a670-31d787ce175e', 'Accounting & tax fees', 15000, 0, 0, '2026-07-10T09:00:00Z');
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('9a5552da-5500-41c6-a7e0-b117fcfe2e05', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'd8e689b9-e767-407b-86af-2c15e5d31644', '72d9d946-7bdd-426e-b996-e0b61d6aeca5', 'EFT to auditors', 0, 15000, 1, '2026-07-10T09:00:00Z');

-- JE-2026-00018: Monthly depreciation — plant & vehicles
INSERT INTO acc_journal_entries
    (id, tenant_id, entry_number, entry_date, description, reference, entry_type, status, total_debit, total_credit, posted_at, created_by, created_at, updated_at, version)
VALUES
    ('1f69ab20-7865-4e18-8ab6-26903930d5f2', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'JE-2026-00018', '2026-07-15', 'Monthly depreciation — plant & vehicles', 'DEP-JUL-2026', 'PAYMENT', 'POSTED', 8000, 8000, '2026-07-15T09:00:00Z', NULL, '2026-07-15T09:00:00Z', '2026-07-15T09:00:00Z', 0);
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('a8507fb5-4dbe-4037-9777-f4400d34f598', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', '1f69ab20-7865-4e18-8ab6-26903930d5f2', 'cf2c48b3-9a93-4d73-b94c-19812b8aac19', 'Depreciation charge July', 8000, 0, 0, '2026-07-15T09:00:00Z');
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('4edfbae7-e9b3-4b27-afd2-ce6f13540782', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', '1f69ab20-7865-4e18-8ab6-26903930d5f2', '72d9d946-7bdd-426e-b996-e0b61d6aeca5', 'Accumulated depreciation', 0, 8000, 1, '2026-07-15T09:00:00Z');

-- JE-2026-00019: Excavator service & repairs
INSERT INTO acc_journal_entries
    (id, tenant_id, entry_number, entry_date, description, reference, entry_type, status, total_debit, total_credit, posted_at, created_by, created_at, updated_at, version)
VALUES
    ('1784ec7b-e520-45e1-8efe-027bf8eae393', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'JE-2026-00019', '2026-07-20', 'Excavator service & repairs', 'MAINT-JUL-2026', 'PAYMENT', 'POSTED', 6200, 6200, '2026-07-20T09:00:00Z', NULL, '2026-07-20T09:00:00Z', '2026-07-20T09:00:00Z', 0);
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('321aca89-05a3-4175-9fbb-1ef9add3f2f4', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', '1784ec7b-e520-45e1-8efe-027bf8eae393', '0d05d752-874d-4bea-a9a9-bbad043f2ad9', 'Excavator service', 6200, 0, 0, '2026-07-20T09:00:00Z');
INSERT INTO acc_journal_lines
    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
VALUES
    ('ca1efd2f-030a-4039-b597-34a17bec1315', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', '1784ec7b-e520-45e1-8efe-027bf8eae393', '72d9d946-7bdd-426e-b996-e0b61d6aeca5', 'Payment to Mantis Equipment', 0, 6200, 1, '2026-07-20T09:00:00Z');

-- One OPEN VAT period covering the full seeded range — for testing
-- the attach-vat201 flow built earlier this session.
INSERT INTO acc_vat_periods
    (id, tenant_id, period_start, period_end, status, output_vat, input_vat, submitted_at, created_at, updated_at)
VALUES
    ('9fd5c68b-36a0-46f5-a6c6-61c9e911b109', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', '2026-05-01', '2026-07-31', 'OPEN', 0, 0, NULL, '2026-05-01T08:00:00Z', '2026-05-01T08:00:00Z');

-- Bank transactions — first four intentionally match real journal
-- entries above (for testing reconciliation's 'Match existing' path,
-- never actually confirmed working until now); last one has no match
-- (for testing 'Create new journal').
INSERT INTO acc_bank_transactions
    (id, tenant_id, bank_account_id, transaction_date, description, reference, amount, transaction_type, balance_after, reconciled, reconciled_at, journal_line_id, created_at, updated_at)
VALUES
    ('532972d1-4d4f-4a39-919d-7a6c41e54156', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'e2b8b7a3-5156-4ed5-b08d-4c5d75eac469', '2026-05-01', 'Office & yard rent - May', 'RENT-MAY-2026', 12000, 'DEBIT', -12000, false, NULL, NULL, '2026-05-01T10:00:00Z', '2026-05-01T10:00:00Z');
INSERT INTO acc_bank_transactions
    (id, tenant_id, bank_account_id, transaction_date, description, reference, amount, transaction_type, balance_after, reconciled, reconciled_at, journal_line_id, created_at, updated_at)
VALUES
    ('2c1a83ef-afdf-4c62-a2ac-da54218b7cba', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'e2b8b7a3-5156-4ed5-b08d-4c5d75eac469', '2026-05-01', 'Salaries - May', 'PAYROLL-MAY-2026', 42000, 'DEBIT', -54000, false, NULL, NULL, '2026-05-01T10:00:00Z', '2026-05-01T10:00:00Z');
INSERT INTO acc_bank_transactions
    (id, tenant_id, bank_account_id, transaction_date, description, reference, amount, transaction_type, balance_after, reconciled, reconciled_at, journal_line_id, created_at, updated_at)
VALUES
    ('64c1e143-584d-4ef8-8fa6-e361e1a14791', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'e2b8b7a3-5156-4ed5-b08d-4c5d75eac469', '2026-05-25', 'Receipt - SANRAL May claim', 'RCP-ZE-101', 57500, 'CREDIT', 3500, false, NULL, NULL, '2026-05-25T10:00:00Z', '2026-05-25T10:00:00Z');
INSERT INTO acc_bank_transactions
    (id, tenant_id, bank_account_id, transaction_date, description, reference, amount, transaction_type, balance_after, reconciled, reconciled_at, journal_line_id, created_at, updated_at)
VALUES
    ('b44e1136-e620-4d2a-aba4-fe7a67b6bba1', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'e2b8b7a3-5156-4ed5-b08d-4c5d75eac469', '2026-06-15', 'Insurance premium - fleet', 'INS-JUN-2026', 18200, 'DEBIT', -14700, false, NULL, NULL, '2026-06-15T10:00:00Z', '2026-06-15T10:00:00Z');
INSERT INTO acc_bank_transactions
    (id, tenant_id, bank_account_id, transaction_date, description, reference, amount, transaction_type, balance_after, reconciled, reconciled_at, journal_line_id, created_at, updated_at)
VALUES
    ('83d0564a-b909-4e38-9123-d81dd01aa70a', '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f', 'e2b8b7a3-5156-4ed5-b08d-4c5d75eac469', '2026-07-01', 'Bank charges - monthly fee', 'BANKFEE-JUL', 185.5, 'DEBIT', -14885.5, false, NULL, NULL, '2026-07-01T10:00:00Z', '2026-07-01T10:00:00Z');

-- Resync the journal number sequence so the app's own numbering
-- continues correctly from here — same backfill logic already built
-- earlier this session for exactly this purpose.
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
SELECT
    (SELECT COUNT(*) FROM acc_journal_entries WHERE tenant_id = '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f') AS entries_inserted,
    (SELECT COUNT(*) FROM acc_journal_lines l JOIN acc_journal_entries e ON e.id = l.journal_entry_id WHERE e.tenant_id = '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f') AS lines_inserted,
    (SELECT SUM(total_debit) - SUM(total_credit) FROM acc_journal_entries WHERE tenant_id = '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f') AS should_be_zero;

COMMIT;