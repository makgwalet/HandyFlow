-- ============================================================
-- V99__crm_qa_test_data.sql
-- CRM QA Test Data Seed
--
-- PURPOSE:
--   Provides deterministic, realistic test data covering every
--   customer state, edge case, and UI scenario for QA sign-off.
--
-- USAGE:
--   Run ONLY in dev/staging — NEVER in production.
--   Add to: src/main/resources/db/testdata/ (not db/migration/)
--   Load via Spring profile: spring.sql.init.data-locations=classpath:db/testdata/V99__crm_qa_test_data.sql
--   Or run manually: psql -d handyflow_dev -f V99__crm_qa_test_data.sql
--
-- TENANT SETUP:
--   Two tenants — keeps data isolated so tenant isolation can be tested.
--   TENANT A (primary QA tenant): f47ac10b-58cc-4372-a567-0e02b2c3d479
--   TENANT B (isolation test tenant): a1b2c3d4-1234-5678-9abc-def012345678
-- ============================================================

BEGIN;

-- ── Convenience variables ────────────────────────────────────────────────────
DO $$
DECLARE
    tenant_a UUID := 'f47ac10b-58cc-4372-a567-0e02b2c3d479';
    tenant_b UUID := 'a1b2c3d4-1234-5678-9abc-def012345678';
    sys_user UUID := '00000000-0000-0000-0000-000000000001'; -- "system" user for seeded activities
BEGIN

-- ============================================================
-- SECTION 1: TENANT A — ACTIVE CUSTOMERS (status = ACTIVE, type = CUSTOMER)
-- Covers: normal happy-path display, search, pagination
-- ============================================================

-- 1. Full data — every field populated. The "perfect record" for visual QA.
INSERT INTO customers (id, tenant_id, name, email, phone, address, tax_number, notes, customer_type, status, created_at, updated_at, version)
VALUES (
    'c1000001-0000-0000-0000-000000000001', tenant_a,
    'Tau Mining (Pty) Ltd',
    'accounts@taumining.co.za',
    '+27 18 788 1234',
    '{"street":"45 Mine Road","suburb":"Carletonville","city":"Merafong","province":"Gauteng","postalCode":"2499"}',
    '4198765432',
    'Key account. Prefers invoices on the 25th. Contact: Johan van der Merwe (CFO).',
    'CUSTOMER', 'ACTIVE',
    now() - interval '18 months', now() - interval '2 days', 0
);

-- 2. Large corporate — long name, tests text truncation in table
INSERT INTO customers (id, tenant_id, name, email, phone, address, tax_number, notes, customer_type, status, created_at, updated_at, version)
VALUES (
    'c1000001-0000-0000-0000-000000000002', tenant_a,
    'South African Broadcasting Corporation (SABC) Infrastructure Division',
    'procurement@sabc.co.za',
    '+27 11 714 9111',
    '{"street":"Private Bag X1","suburb":"Auckland Park","city":"Johannesburg","province":"Gauteng","postalCode":"2006"}',
    '4700101378',
    'Government entity. Requires official purchase orders before any work begins. 60-day payment terms.',
    'CUSTOMER', 'ACTIVE',
    now() - interval '12 months', now() - interval '1 week', 0
);

-- 3. Email only — no phone, no address, no VAT. Tests missing-field display ("—")
INSERT INTO customers (id, tenant_id, name, email, phone, address, tax_number, notes, customer_type, status, created_at, updated_at, version)
VALUES (
    'c1000001-0000-0000-0000-000000000003', tenant_a,
    'Sipho Dlamini Electrical',
    'sipho@dlaminielectrical.co.za',
    NULL, NULL, NULL, NULL,
    'CUSTOMER', 'ACTIVE',
    now() - interval '6 months', now() - interval '6 months', 0
);

-- 4. Phone only — no email. Tests email-optional path and search by phone
INSERT INTO customers (id, tenant_id, name, email, phone, address, tax_number, notes, customer_type, status, created_at, updated_at, version)
VALUES (
    'c1000001-0000-0000-0000-000000000004', tenant_a,
    'Nomsa''s Catering Services',
    NULL,
    '082 456 7890',
    '{"city":"Cape Town","province":"Western Cape","postalCode":"8001"}',
    NULL,
    'Cash account. Always pays on delivery. Nomsa prefers WhatsApp: 082 456 7890',
    'CUSTOMER', 'ACTIVE',
    now() - interval '3 months', now() - interval '3 months', 0
);

-- 5. Western Cape customer — tests province dropdown and address display
INSERT INTO customers (id, tenant_id, name, email, phone, address, tax_number, notes, customer_type, status, created_at, updated_at, version)
VALUES (
    'c1000001-0000-0000-0000-000000000005', tenant_a,
    'Cape Winelands Properties',
    'info@capewinelands.co.za',
    '+27 21 887 4455',
    '{"street":"12 Dorp Street","suburb":"Stellenbosch","city":"Stellenbosch","province":"Western Cape","postalCode":"7600"}',
    '4019281764',
    NULL,
    'CUSTOMER', 'ACTIVE',
    now() - interval '8 months', now() - interval '1 month', 0
);

-- 6. KwaZulu-Natal — tests all 9 provinces represented
INSERT INTO customers (id, tenant_id, name, email, phone, address, tax_number, notes, customer_type, status, created_at, updated_at, version)
VALUES (
    'c1000001-0000-0000-0000-000000000006', tenant_a,
    'Durban Port Logistics',
    'ops@dbnportlogistics.co.za',
    '+27 31 301 8800',
    '{"street":"1 Victoria Embankment","suburb":"Point","city":"Durban","province":"KwaZulu-Natal","postalCode":"4001"}',
    '4056789012',
    'Requires 2 days advance scheduling for site access. Security clearance needed.',
    'CUSTOMER', 'ACTIVE',
    now() - interval '2 years', now() - interval '3 weeks', 0
);

-- 7. Very new customer — created today. Tests "just added" display
INSERT INTO customers (id, tenant_id, name, email, phone, address, tax_number, notes, customer_type, status, created_at, updated_at, version)
VALUES (
    'c1000001-0000-0000-0000-000000000007', tenant_a,
    'Lethiwe Construction CC',
    'lethiwe@lconstruction.co.za',
    '083 111 2233',
    '{"street":"Plot 14","suburb":"Atteridgeville","city":"Pretoria","province":"Gauteng","postalCode":"0008"}',
    NULL,
    'Referred by Tau Mining. New account — pending first invoice.',
    'CUSTOMER', 'ACTIVE',
    now(), now(), 0
);

-- 8. Many tags — tests tag overflow display (+N) and tag list wrapping
INSERT INTO customers (id, tenant_id, name, email, phone, address, tax_number, notes, customer_type, status, created_at, updated_at, version)
VALUES (
    'c1000001-0000-0000-0000-000000000008', tenant_a,
    'Anglo American Platinum',
    'vendor.management@angloplat.com',
    '+27 11 373 6111',
    '{"street":"55 Marshall Street","suburb":"Marshalltown","city":"Johannesburg","province":"Gauteng","postalCode":"2107"}',
    '4560192837',
    'Preferred vendor status. Annual contract review in March.',
    'CUSTOMER', 'ACTIVE',
    now() - interval '3 years', now() - interval '5 days', 0
);

INSERT INTO customer_tags (customer_id, tag) VALUES
    ('c1000001-0000-0000-0000-000000000008', 'vip'),
    ('c1000001-0000-0000-0000-000000000008', 'key-account'),
    ('c1000001-0000-0000-0000-000000000008', 'annual-contract'),
    ('c1000001-0000-0000-0000-000000000008', 'preferred-vendor'),
    ('c1000001-0000-0000-0000-000000000008', 'mining'),
    ('c1000001-0000-0000-0000-000000000008', 'jse-listed');

-- 9. Customer with "vip" and "overdue" tags — tests mixed tag scenarios
INSERT INTO customers (id, tenant_id, name, email, phone, address, tax_number, notes, customer_type, status, created_at, updated_at, version)
VALUES (
    'c1000001-0000-0000-0000-000000000009', tenant_a,
    'Transnet Freight Rail',
    'accounts.payable@transnet.net',
    '+27 11 584 0000',
    '{"street":"1 Old Pretoria Road","suburb":"Halfway House","city":"Midrand","province":"Gauteng","postalCode":"1685"}',
    '4510246802',
    'SOE — State Owned Entity. Slow payer, 90-day cycle. Always pays eventually.',
    'CUSTOMER', 'ACTIVE',
    now() - interval '4 years', now() - interval '2 months', 0
);

INSERT INTO customer_tags (customer_id, tag) VALUES
    ('c1000001-0000-0000-0000-000000000009', 'vip'),
    ('c1000001-0000-0000-0000-000000000009', 'overdue'),
    ('c1000001-0000-0000-0000-000000000009', 'soe');

-- 10. No notes, no tags — clean minimal record
INSERT INTO customers (id, tenant_id, name, email, phone, address, tax_number, notes, customer_type, status, created_at, updated_at, version)
VALUES (
    'c1000001-0000-0000-0000-000000000010', tenant_a,
    'Zakhele IT Solutions',
    'zakhele@zakhele-it.co.za',
    '071 234 5678',
    '{"city":"Polokwane","province":"Limpopo","postalCode":"0699"}',
    NULL, NULL,
    'CUSTOMER', 'ACTIVE',
    now() - interval '1 year', now() - interval '1 year', 0
);

-- ── Padding to 15 active customers to test pagination (PAGE_SIZE = 10) ───────
INSERT INTO customers (id, tenant_id, name, email, phone, address, tax_number, notes, customer_type, status, created_at, updated_at, version)
VALUES
    ('c1000001-0000-0000-0000-000000000011', tenant_a, 'Abakwe Security Services', 'info@abakwe.co.za', '+27 12 345 0011', '{"city":"Pretoria","province":"Gauteng","postalCode":"0001"}', NULL, NULL, 'CUSTOMER', 'ACTIVE', now() - interval '14 months', now() - interval '14 months', 0),
    ('c1000001-0000-0000-0000-000000000012', tenant_a, 'Blue Label Telecoms', 'procurement@bluelabel.com', '+27 11 523 3000', '{"street":"75 Grayston Drive","city":"Sandton","province":"Gauteng","postalCode":"2196"}', '4123456789', NULL, 'CUSTOMER', 'ACTIVE', now() - interval '20 months', now() - interval '20 months', 0),
    ('c1000001-0000-0000-0000-000000000013', tenant_a, 'Clover Industries Ltd', 'accounts@clover.co.za', '+27 11 278 3000', '{"city":"Johannesburg","province":"Gauteng","postalCode":"2000"}', '4987654321', 'Dairy & FMCG. Requires refrigerated-area compliance certs.', 'CUSTOMER', 'ACTIVE', now() - interval '10 months', now() - interval '10 months', 0),
    ('c1000001-0000-0000-0000-000000000014', tenant_a, 'DataProphet (Pty) Ltd', 'finance@dataprophet.com', NULL, '{"city":"Cape Town","province":"Western Cape","postalCode":"8001"}', NULL, 'AI/ML startup — fast-growing. Net 30 terms.', 'CUSTOMER', 'ACTIVE', now() - interval '5 months', now() - interval '5 months', 0),
    ('c1000001-0000-0000-0000-000000000015', tenant_a, 'Eskom Holdings SOC Ltd', 'vendor.reg@eskom.co.za', '+27 11 800 8111', '{"street":"Megawatt Park","suburb":"Maxwell Drive","city":"Sandton","province":"Gauteng","postalCode":"2157"}', '4070001006', 'Requires CSD registration. Very long procurement process — plan 6 months ahead.', 'CUSTOMER', 'ACTIVE', now() - interval '5 years', now() - interval '1 week', 0);

INSERT INTO customer_tags (customer_id, tag) VALUES
    ('c1000001-0000-0000-0000-000000000015', 'soe'),
    ('c1000001-0000-0000-0000-000000000015', 'key-account'),
    ('c1000001-0000-0000-0000-000000000015', 'vip');

-- ============================================================
-- SECTION 2: LEADS (customer_type = LEAD, status = ACTIVE)
-- Covers: lead vs customer badge distinction, conversion flow
-- ============================================================

INSERT INTO customers (id, tenant_id, name, email, phone, address, tax_number, notes, customer_type, status, created_at, updated_at, version)
VALUES
    (
        'c2000001-0000-0000-0000-000000000001', tenant_a,
        'Motsepe Foundation',
        'enquiries@motsepe.org.za',
        '+27 11 883 7800',
        '{"city":"Johannesburg","province":"Gauteng","postalCode":"2000"}',
        NULL,
        'Warm lead — met at Mining Indaba 2025. Follow up before end of Q3.',
        'LEAD', 'ACTIVE',
        now() - interval '2 weeks', now() - interval '2 weeks', 0
    ),
    (
        'c2000001-0000-0000-0000-000000000002', tenant_a,
        'Clicks Group Services',
        'supplier.dev@clicksgroup.co.za',
        '+27 21 460 1911',
        '{"street":"Clicks House, Kirstenhof","city":"Cape Town","province":"Western Cape","postalCode":"7945"}',
        NULL,
        'Cold outreach — responded positively. Waiting for RFQ.',
        'LEAD', 'ACTIVE',
        now() - interval '1 month', now() - interval '1 month', 0
    ),
    (
        'c2000001-0000-0000-0000-000000000003', tenant_a,
        'Mbeki & Associates Legal',
        'office@mbeki-legal.co.za',
        '082 999 0000',
        '{"suburb":"Rosebank","city":"Johannesburg","province":"Gauteng","postalCode":"2196"}',
        NULL,
        'Inbound lead from website. Interested in monthly retainer.',
        'LEAD', 'ACTIVE',
        now() - interval '3 days', now() - interval '3 days', 0
    );

INSERT INTO customer_tags (customer_id, tag) VALUES
    ('c2000001-0000-0000-0000-000000000001', 'hot-lead'),
    ('c2000001-0000-0000-0000-000000000002', 'rfq-pending'),
    ('c2000001-0000-0000-0000-000000000003', 'inbound');

-- ============================================================
-- SECTION 3: INACTIVE CUSTOMERS (status = INACTIVE)
-- Covers: inactivity badge, scheduler logic, outreach workflow
-- ============================================================

INSERT INTO customers (id, tenant_id, name, email, phone, address, tax_number, notes, customer_type, status, created_at, updated_at, version)
VALUES
    (
        'c3000001-0000-0000-0000-000000000001', tenant_a,
        'Fedics Catering Group',
        'contracts@fedics.co.za',
        '+27 11 554 7700',
        '{"street":"22 Electron Avenue","suburb":"Isando","city":"Ekurhuleni","province":"Gauteng","postalCode":"1600"}',
        '4310028754',
        'Was a regular monthly client. No bookings since Oct 2024. Follow up urgently.',
        'CUSTOMER', 'INACTIVE',
        now() - interval '3 years', now() - interval '95 days', 0  -- > 90 days = auto-inactive threshold
    ),
    (
        'c3000001-0000-0000-0000-000000000002', tenant_a,
        'Primedia Broadcasting',
        'finance@primedia.co.za',
        '+27 11 543 0000',
        '{"city":"Johannesburg","province":"Gauteng","postalCode":"2000"}',
        '4290145236',
        'Seasonal client — usually active Q1 and Q4. Currently off-cycle.',
        'CUSTOMER', 'INACTIVE',
        now() - interval '2 years', now() - interval '120 days', 0
    );

INSERT INTO customer_tags (customer_id, tag) VALUES
    ('c3000001-0000-0000-0000-000000000001', 'inactive'),
    ('c3000001-0000-0000-0000-000000000001', 'follow-up'),
    ('c3000001-0000-0000-0000-000000000002', 'seasonal');

-- ============================================================
-- SECTION 4: BLOCKED CUSTOMER (status = BLOCKED)
-- Covers: blocked badge (red avatar), "do not transact" scenario
-- ============================================================

INSERT INTO customers (id, tenant_id, name, email, phone, address, tax_number, notes, customer_type, status, created_at, updated_at, version)
VALUES (
    'c4000001-0000-0000-0000-000000000001', tenant_a,
    'Falcon Freight (Pty) Ltd',
    'info@falconfreight.co.za',
    '+27 11 392 4567',
    '{"street":"Unit 5, Pomona Rd","suburb":"Pomona","city":"Ekurhuleni","province":"Gauteng","postalCode":"1619"}',
    '4890234567',
    'BLOCKED: Unpaid invoices INV-0201, INV-0215 totalling R48,500. Referred to collections. Do NOT accept new bookings.',
    'CUSTOMER', 'BLOCKED',
    now() - interval '1 year', now() - interval '30 days', 0
);

INSERT INTO customer_tags (customer_id, tag) VALUES
    ('c4000001-0000-0000-0000-000000000001', 'blocked'),
    ('c4000001-0000-0000-0000-000000000001', 'bad-debt'),
    ('c4000001-0000-0000-0000-000000000001', 'collections');

-- ============================================================
-- SECTION 5: SOFT-DELETED CUSTOMERS
-- Covers: "Deleted" tab, restore functionality, deleted_at display
-- ============================================================

INSERT INTO customers (id, tenant_id, name, email, phone, address, tax_number, notes, customer_type, status, created_at, updated_at, deleted_at, deleted_by, version)
VALUES
    (
        'c5000001-0000-0000-0000-000000000001', tenant_a,
        'Redundant Roofing CC',
        'admin@redundantroofing.co.za',
        '083 000 1111',
        '{"city":"Johannesburg","province":"Gauteng","postalCode":"2000"}',
        NULL, 'Merged into parent company. Replaced by JSE Listed Holdings.',
        'CUSTOMER', 'ACTIVE',
        now() - interval '2 years', now() - interval '1 month',
        now() - interval '1 month',  -- deleted_at
        sys_user, 0
    ),
    (
        'c5000001-0000-0000-0000-000000000002', tenant_a,
        'Test Customer — Please Ignore',
        'test@test.com',
        NULL, NULL, NULL, 'Created by mistake during onboarding demo.',
        'CUSTOMER', 'ACTIVE',
        now() - interval '6 months', now() - interval '6 months',
        now() - interval '6 months',
        sys_user, 0
    ),
    (
        'c5000001-0000-0000-0000-000000000003', tenant_a,
        'Linkd Retail Group',
        'finance@linkdretail.co.za',
        '+27 21 555 8800',
        '{"city":"Cape Town","province":"Western Cape","postalCode":"8001"}',
        '4123789456', 'Company went into business rescue. DO NOT restore without legal sign-off.',
        'CUSTOMER', 'ACTIVE',
        now() - interval '4 years', now() - interval '2 weeks',
        now() - interval '2 weeks',
        sys_user, 0
    );

-- ============================================================
-- SECTION 6: ACTIVITY TIMELINE DATA
-- Covers: timeline modal, different activity types, colour coding,
--         note display, status-change detail, cross-module events
-- ============================================================

-- Full timeline for Tau Mining (c1000001-0000-0000-0000-000000000001)
INSERT INTO customer_activities (id, tenant_id, customer_id, activity_type, payload, note, performed_by, created_at)
VALUES
    -- Created
    (gen_random_uuid(), tenant_a, 'c1000001-0000-0000-0000-000000000001',
     'CREATED', NULL, NULL, sys_user,
     now() - interval '18 months'),

    -- First booking linked (simulates BookingService calling CrmFacade)
    (gen_random_uuid(), tenant_a, 'c1000001-0000-0000-0000-000000000001',
     'BOOKING_LINKED', '{"bookingId":"b0000001-0000-0000-0000-000000000001"}', NULL, sys_user,
     now() - interval '17 months'),

    -- Staff note added
    (gen_random_uuid(), tenant_a, 'c1000001-0000-0000-0000-000000000001',
     'NOTE_ADDED', NULL, 'Called Johan re: Q3 scope. He confirmed they need 3 additional substations surveyed before year-end.', sys_user,
     now() - interval '14 months'),

    -- Details updated (email change)
    (gen_random_uuid(), tenant_a, 'c1000001-0000-0000-0000-000000000001',
     'UPDATED', '{"email":{"from":"old@taumining.co.za","to":"accounts@taumining.co.za"}}', NULL, sys_user,
     now() - interval '10 months'),

    -- Tag added
    (gen_random_uuid(), tenant_a, 'c1000001-0000-0000-0000-000000000001',
     'TAG_ADDED', '{"tag":"key-account"}', NULL, sys_user,
     now() - interval '10 months'),

    -- Invoice linked
    (gen_random_uuid(), tenant_a, 'c1000001-0000-0000-0000-000000000001',
     'INVOICE_LINKED', '{"invoiceId":"i0000001-0000-0000-0000-000000000001"}', NULL, sys_user,
     now() - interval '8 months'),

    -- Second booking
    (gen_random_uuid(), tenant_a, 'c1000001-0000-0000-0000-000000000001',
     'BOOKING_LINKED', '{"bookingId":"b0000001-0000-0000-0000-000000000002"}', NULL, sys_user,
     now() - interval '5 months'),

    -- Staff note — follow up
    (gen_random_uuid(), tenant_a, 'c1000001-0000-0000-0000-000000000001',
     'NOTE_ADDED', NULL, 'Visited site in Carletonville. New contact person: Thandi Nkosi (replaces Johan who retired).', sys_user,
     now() - interval '2 months'),

    -- Most recent activity
    (gen_random_uuid(), tenant_a, 'c1000001-0000-0000-0000-000000000001',
     'NOTE_ADDED', NULL, 'Sent Q4 proposal. Awaiting sign-off from procurement committee.', sys_user,
     now() - interval '2 days');


-- Timeline for Falcon Freight — the BLOCKED customer
INSERT INTO customer_activities (id, tenant_id, customer_id, activity_type, payload, note, performed_by, created_at)
VALUES
    (gen_random_uuid(), tenant_a, 'c4000001-0000-0000-0000-000000000001',
     'CREATED', NULL, NULL, sys_user,
     now() - interval '1 year'),
    (gen_random_uuid(), tenant_a, 'c4000001-0000-0000-0000-000000000001',
     'BOOKING_LINKED', '{"bookingId":"b9999999-0000-0000-0000-000000000001"}', NULL, sys_user,
     now() - interval '9 months'),
    (gen_random_uuid(), tenant_a, 'c4000001-0000-0000-0000-000000000001',
     'INVOICE_LINKED', '{"invoiceId":"i9999999-0000-0000-0000-000000000001"}', NULL, sys_user,
     now() - interval '8 months'),
    (gen_random_uuid(), tenant_a, 'c4000001-0000-0000-0000-000000000001',
     'NOTE_ADDED', NULL, 'INV-0201 overdue by 30 days. Sent reminder email.', sys_user,
     now() - interval '6 months'),
    (gen_random_uuid(), tenant_a, 'c4000001-0000-0000-0000-000000000001',
     'NOTE_ADDED', NULL, 'No response to 3 emails and 2 phone calls. Escalated to management.', sys_user,
     now() - interval '5 months'),
    (gen_random_uuid(), tenant_a, 'c4000001-0000-0000-0000-000000000001',
     'TAG_ADDED', '{"tag":"bad-debt"}', NULL, sys_user,
     now() - interval '4 months'),
    (gen_random_uuid(), tenant_a, 'c4000001-0000-0000-0000-000000000001',
     'STATUS_CHANGED', '{"from":"ACTIVE","to":"BLOCKED"}', NULL, sys_user,
     now() - interval '30 days');


-- Minimal timeline for a new customer (Lethiwe — created today)
INSERT INTO customer_activities (id, tenant_id, customer_id, activity_type, payload, note, performed_by, created_at)
VALUES (
    gen_random_uuid(), tenant_a, 'c1000001-0000-0000-0000-000000000007',
    'CREATED', NULL, NULL, sys_user, now()
);

-- ============================================================
-- SECTION 7: CONTACTS (linked to customers)
-- Covers: multi-contact customer records
-- ============================================================

INSERT INTO contacts (id, tenant_id, customer_id, first_name, last_name, email, phone, job_title, is_primary, notes, created_at, updated_at, version)
VALUES
    -- Tau Mining contacts
    (gen_random_uuid(), tenant_a, 'c1000001-0000-0000-0000-000000000001',
     'Thandi', 'Nkosi', 'thandi.nkosi@taumining.co.za', '+27 82 111 2222',
     'Procurement Manager', TRUE, 'Primary contact since Johan retired.', now() - interval '2 months', now() - interval '2 months', 0),
    (gen_random_uuid(), tenant_a, 'c1000001-0000-0000-0000-000000000001',
     'Pieter', 'van Zyl', 'pieter.vanzyl@taumining.co.za', '+27 72 333 4444',
     'Site Manager', FALSE, 'On-site contact for scheduling access.', now() - interval '6 months', now() - interval '6 months', 0),

    -- Eskom — complex org structure
    (gen_random_uuid(), tenant_a, 'c1000001-0000-0000-0000-000000000015',
     'Nompumelelo', 'Dlamini', 'n.dlamini@eskom.co.za', '+27 11 800 1234',
     'Vendor Registration Officer', TRUE, 'Handles all CSD and vendor paperwork.', now() - interval '2 years', now() - interval '2 years', 0),
    (gen_random_uuid(), tenant_a, 'c1000001-0000-0000-0000-000000000015',
     'Luyanda', 'Mthembu', 'l.mthembu@eskom.co.za', '+27 11 800 5678',
     'Senior Buyer', FALSE, 'Signs off on purchase orders above R50k.', now() - interval '1 year', now() - interval '1 year', 0);

-- ============================================================
-- SECTION 8: TENANT B — ISOLATION TEST DATA
-- QA Test: Tenant A users must NOT see Tenant B records.
-- Search, list, and direct ID lookup must all return empty for Tenant A.
-- ============================================================

INSERT INTO customers (id, tenant_id, name, email, phone, address, tax_number, notes, customer_type, status, created_at, updated_at, version)
VALUES (
    'cb000001-0000-0000-0000-000000000001', tenant_b,
    'Tenant B Company — SHOULD NOT BE VISIBLE TO TENANT A',
    'admin@tenantb.co.za',
    '+27 11 000 0000',
    '{"city":"Johannesburg","province":"Gauteng","postalCode":"2000"}',
    '4000000001',
    'If a Tenant A user can see this record, multi-tenancy is BROKEN.',
    'CUSTOMER', 'ACTIVE',
    now(), now(), 0
);

END;
$$;

COMMIT;

-- ============================================================
-- QUICK-REFERENCE: Test scenario index for QA team
-- ============================================================
--
-- ┌─────────────────────────────────────────────────────────────────────────┐
-- │  SCENARIO                         │ CUSTOMER NAME / ID                  │
-- ├─────────────────────────────────────────────────────────────────────────┤
-- │  Full record (all fields set)     │ Tau Mining (Pty) Ltd                │
-- │  Long name — truncation test      │ South African Broadcasting Corp...  │
-- │  Email only (no phone/address)    │ Sipho Dlamini Electrical            │
-- │  Phone only (no email)            │ Nomsa's Catering Services           │
-- │  New customer (created today)     │ Lethiwe Construction CC             │
-- │  6+ tags (overflow display)       │ Anglo American Platinum             │
-- │  LEAD type badge                  │ Motsepe Foundation                  │
-- │  LEAD — inbound                   │ Mbeki & Associates Legal            │
-- │  INACTIVE status badge            │ Fedics Catering Group               │
-- │  INACTIVE — seasonal              │ Primedia Broadcasting               │
-- │  BLOCKED (red avatar + badge)     │ Falcon Freight (Pty) Ltd           │
-- │  Rich activity timeline           │ Tau Mining (Pty) Ltd                │
-- │  Status change in timeline        │ Falcon Freight (Pty) Ltd           │
-- │  Deleted — accidental             │ Test Customer — Please Ignore       │
-- │  Deleted — business rescue        │ Linkd Retail Group                  │
-- │  Deleted — company merge          │ Redundant Roofing CC                │
-- │  Pagination (page 2)              │ All 15 active records in Tenant A   │
-- │  Multi-tenant isolation           │ Tenant B Company (invisible to A)   │
-- │  Multi-contact customer           │ Tau Mining + Eskom                  │
-- │  VAT number display               │ Tau Mining, Eskom, Anglo American   │
-- │  Province: KwaZulu-Natal          │ Durban Port Logistics               │
-- │  Province: Western Cape           │ Cape Winelands Properties           │
-- │  Province: Limpopo                │ Zakhele IT Solutions                │
-- └─────────────────────────────────────────────────────────────────────────┘
--
-- SEARCH TERMS TO TEST:
--   "tau"         → Tau Mining only
--   "mining"      → Tau Mining + Anglo American Platinum (both have "mining" in name/tags)
--   "4198765432"  → Tau Mining (search by VAT number)
--   "+27 11"      → Multiple Gauteng companies
--   "cape"        → Cape Winelands + Durban (no) — tests partial word match
--   "xyz_no_match"→ Empty state / no results display
--   ""            → All 15 active records across 2 pages
-- ============================================================