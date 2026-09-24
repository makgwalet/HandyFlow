
-- ═══════════════════════════════════════════════════════════════════
-- ONE-TIME SEED DATA for compliancetender and complianceservices —
-- run manually via psql, NOT a Flyway migration. Matches the exact
-- convention seed-accounting-data.sql already established: hardcoded
-- IDs (via a DO block with DECLARE, matching V99's own multi-section
-- style), scoped to the zeta-earthmoving pilot tenant.
--
-- PREREQUISITE: V300__activate_compliance_modules_for_zeta.sql must
-- have run first (it's a normal Flyway migration, ships with the app
-- and runs automatically on next `mvn spring-boot:run` / `mvn test`).
-- Without it, the seed data below inserts fine (raw SQL bypasses
-- FeatureGuard entirely), but the UI and API will return 403 for both
-- modules until that migration has actually run — FeatureGuard is an
-- application-layer check against tenant_modules, not something this
-- script can satisfy on its own.
--
-- ALSO SEEDS 2 hr_employees for zeta-earthmoving, as a genuine
-- prerequisite for tender_personnel / client_tender_personnel — this
-- tenant had ZERO employees seeded anywhere before this script.
-- Clearly scoped/commented below so it's not mistaken for unrelated
-- HR seed data.
--
-- DOCUMENT/EVIDENCE LIMITATION, stated plainly rather than left
-- implicit: compliance_documents / client_compliance_documents rows
-- reference evidence rows with a FABRICATED storage_key — there is no
-- real file behind them. Every list/verify/filter UI works correctly
-- against this seed data; clicking an actual "download" link on a
-- seeded document will fail, because EvidenceFacade's real storage
-- backend was never given real bytes to store. Seeding a genuinely
-- downloadable file would mean going through the real upload pipeline
-- (multipart POST through EvidenceFacade.attach), not a raw SQL
-- INSERT — out of scope for what a one-time SQL script can do.
--
-- Run reset-compliance-data.sql first if you want a clean slate.
-- ═══════════════════════════════════════════════════════════════════

BEGIN;

DO $$
DECLARE
    tenant_id   UUID;
    sys_user    UUID := '00000000-0000-0000-0000-000000000001';

    -- ── HR prerequisite ──────────────────────────────────────────────
    emp_thabo   UUID := gen_random_uuid();
    emp_naledi  UUID := gen_random_uuid();

    -- ── compliancetender (Zeta's own compliance) ────────────────────
    reg_cipc    UUID := gen_random_uuid();
    reg_sars    UUID := gen_random_uuid();
    reg_psira   UUID := gen_random_uuid();
    reg_csd     UUID := gen_random_uuid();
    reg_cidb    UUID := gen_random_uuid();

    ev_doc1     UUID := gen_random_uuid();
    ev_doc2     UUID := gen_random_uuid();
    ev_doc3     UUID := gen_random_uuid();
    doc1        UUID := gen_random_uuid();
    doc2        UUID := gen_random_uuid();
    doc3        UUID := gen_random_uuid();

    dl1         UUID := gen_random_uuid();
    dl2         UUID := gen_random_uuid();
    dl3         UUID := gen_random_uuid();
    dl4         UUID := gen_random_uuid();

    req1        UUID := gen_random_uuid();
    req2        UUID := gen_random_uuid();
    req3        UUID := gen_random_uuid();

    tnd_draft   UUID := gen_random_uuid();
    tnd_prep    UUID := gen_random_uuid();
    tnd_sub     UUID := gen_random_uuid();

    -- ── complianceservices (Zeta as service provider) ───────────────
    client_acme UUID := gen_random_uuid();
    client_tau  UUID := gen_random_uuid();

    creg1       UUID := gen_random_uuid();
    creg2       UUID := gen_random_uuid();
    creg3       UUID := gen_random_uuid();

    cev_doc1    UUID := gen_random_uuid();
    cdoc1       UUID := gen_random_uuid();

    cdl1        UUID := gen_random_uuid();
    cdl2        UUID := gen_random_uuid();

    creq1       UUID := gen_random_uuid();
    creq2       UUID := gen_random_uuid();

    ctnd_draft  UUID := gen_random_uuid();
    ctnd_sub    UUID := gen_random_uuid();

BEGIN
    SELECT id INTO tenant_id FROM tenants WHERE slug = 'zeta-earthmoving';
    IF tenant_id IS NULL THEN
        RAISE EXCEPTION 'zeta-earthmoving tenant not found — run the normal app migrations first';
    END IF;

-- ═══════════════════════════════════════════════════════════════
-- PREREQUISITE: HR employees (zeta-earthmoving had none seeded)
-- ═══════════════════════════════════════════════════════════════
INSERT INTO hr_employees (id, tenant_id, employee_number, first_name, last_name, email, phone,
    employment_type, job_title, department, start_date, status, salary_type, gross_salary, pay_frequency)
VALUES
    (emp_thabo, tenant_id, 'EMP-0001', 'Thabo', 'Mokoena', 'thabo.mokoena@zetaearthmoving.co.za', '0821234567',
     'PERMANENT', 'Project Manager', 'Operations', '2022-03-01', 'ACTIVE', 'MONTHLY', 38000, 'MONTHLY'),
    (emp_naledi, tenant_id, 'EMP-0002', 'Naledi', 'Dube', 'naledi.dube@zetaearthmoving.co.za', '0837654321',
     'PERMANENT', 'Site Agent', 'Operations', '2023-06-15', 'ACTIVE', 'MONTHLY', 32000, 'MONTHLY');

-- ═══════════════════════════════════════════════════════════════
-- compliancetender — Zeta's own compliance registrations
-- ═══════════════════════════════════════════════════════════════
INSERT INTO compliance_registrations (id, tenant_id, authority, registration_type, registration_number,
    status, issued_date, expiry_date, notes, created_by, updated_by)
VALUES
    (reg_cipc,  tenant_id, 'CIPC',  'Business Registration', '2019/456321/07', 'ACTIVE',
     '2019-02-14', NULL, 'Company registration — no expiry.', sys_user, sys_user),
    (reg_sars,  tenant_id, 'SARS',  'Tax Clearance Certificate', 'TCC-2026-04471', 'ACTIVE',
     '2026-04-01', '2027-04-01', NULL, sys_user, sys_user),
    (reg_psira, tenant_id, 'PSIRA', 'Business Registration', 'PSIRA-88213', 'ACTIVE',
     '2025-10-10', CURRENT_DATE + INTERVAL '18 days', 'Renewal pack already with PSIRA.', sys_user, sys_user),
    (reg_csd,   tenant_id, 'CSD',   'Supplier Registration', 'MAAA0123456', 'EXPIRED',
     '2024-01-01', CURRENT_DATE - INTERVAL '40 days', 'Renewal overdue — flagged to admin.', sys_user, sys_user),
    (reg_cidb,  tenant_id, 'CIDB',  'Grade 6GB Registration', 'CIDB-9E4471', 'ACTIVE',
     '2025-01-20', '2027-01-20', NULL, sys_user, sys_user);

-- Documents — evidence rows use a FABRICATED storage_key, see the
-- header note. List/verify/filter UI all work against this data;
-- actual file download will not.
INSERT INTO evidence (id, tenant_id, file_name, content_type, file_size_bytes, storage_key,
    evidence_type, source_module, related_entity_type, related_entity_id, file_hash, uploaded_by, uploaded_by_name)
VALUES
    (ev_doc1, tenant_id, 'sars-tax-clearance-2026.pdf', 'application/pdf', 184320,
     'seed-data/compliancetender/sars-tax-clearance-2026.pdf', 'Tax Clearance Certificate',
     'compliancetender', 'ComplianceDocument', doc1, 'seed0000000000000000000000000000000000000000000000000000001', sys_user, 'Seed Script'),
    (ev_doc2, tenant_id, 'psira-registration-cert.pdf', 'application/pdf', 97231,
     'seed-data/compliancetender/psira-registration-cert.pdf', 'PSIRA Registration Certificate',
     'compliancetender', 'ComplianceDocument', doc2, 'seed0000000000000000000000000000000000000000000000000000002', sys_user, 'Seed Script'),
    (ev_doc3, tenant_id, 'cidb-certificate-grade6gb.pdf', 'application/pdf', 152004,
     'seed-data/compliancetender/cidb-certificate-grade6gb.pdf', 'cidb Certificate',
     'compliancetender', 'ComplianceDocument', doc3, 'seed0000000000000000000000000000000000000000000000000000003', sys_user, 'Seed Script');

INSERT INTO compliance_documents (id, tenant_id, registration_id, document_type, evidence_id,
    issue_date, expiry_date, verified_by, verified_at, created_by, updated_by)
VALUES
    (doc1, tenant_id, reg_sars,  'Tax Clearance Certificate', ev_doc1, '2026-04-01', '2027-04-01', sys_user, NOW(), sys_user, sys_user),
    (doc2, tenant_id, reg_psira, 'PSIRA Registration Certificate', ev_doc2, '2025-10-10', CURRENT_DATE + INTERVAL '18 days', NULL, NULL, sys_user, sys_user),
    (doc3, tenant_id, reg_cidb,  'cidb Certificate', ev_doc3, '2025-01-20', '2027-01-20', sys_user, NOW(), sys_user, sys_user);

-- Deadlines — overdue, due soon, upcoming
INSERT INTO compliance_deadlines (id, tenant_id, registration_id, deadline_type, description, due_date, status, created_by, updated_by)
VALUES
    (dl1, tenant_id, reg_csd,   'RENEWAL', 'CSD supplier registration renewal — overdue', CURRENT_DATE - INTERVAL '40 days', 'PENDING', sys_user, sys_user),
    (dl2, tenant_id, reg_psira, 'RENEWAL', 'PSIRA registration renewal', CURRENT_DATE + INTERVAL '18 days', 'PENDING', sys_user, sys_user),
    (dl3, tenant_id, NULL,      'ANNUAL_RETURN', 'CIPC annual return filing', CURRENT_DATE + INTERVAL '9 days', 'PENDING', sys_user, sys_user),
    (dl4, tenant_id, NULL,      'DECLARATION', 'B-BBEE affidavit renewal', CURRENT_DATE + INTERVAL '75 days', 'PENDING', sys_user, sys_user);

-- Requirement catalogue (versioned)
INSERT INTO compliance_requirements (id, tenant_id, code, name, applies_to, evidence_type, required, requirement_version, created_by, updated_by)
VALUES
    (req1, tenant_id, 'CSD_ACTIVE', 'Valid, active CSD supplier registration', 'Government Tender', 'CSD Registration Certificate', TRUE, 1, sys_user, sys_user),
    (req2, tenant_id, 'CIDB_GRADE', 'CIDB Grade 6GB or higher', 'Construction', 'cidb Certificate', TRUE, 1, sys_user, sys_user),
    (req3, tenant_id, 'TAX_COMPLIANCE', 'Valid SARS Tax Clearance Certificate', 'Government Tender', 'Tax Clearance Certificate', TRUE, 1, sys_user, sys_user);

-- Tenders — one DRAFT, one IN_PREPARATION, one SUBMITTED (with
-- personnel + an automatically-would-be-captured snapshot, seeded
-- explicitly here since this script bypasses the service layer).
INSERT INTO tenders (id, tenant_id, tender_number, name, tender_authority, authority_reference_number,
    closing_date, briefing_date, estimated_value, industry, required_class_of_work, status, created_by, updated_by)
VALUES
    (tnd_draft, tenant_id, 'TND-00001', 'Municipal Stormwater Upgrade — Phase 1', 'City of Tshwane', 'COT-2026-118',
     CURRENT_DATE + INTERVAL '35 days', CURRENT_DATE + INTERVAL '10 days', 8400000, 'Construction', 'cidb Grade 6GB', 'DRAFT', sys_user, sys_user),
    (tnd_prep, tenant_id, 'TND-00002', 'Gravel Road Rehabilitation — District 4', 'Limpopo DRPW', 'LDRPW-2026-042',
     CURRENT_DATE + INTERVAL '21 days', CURRENT_DATE + INTERVAL '5 days', 5200000, 'Construction', 'cidb Grade 5GB', 'IN_PREPARATION', sys_user, sys_user),
    (tnd_sub, tenant_id, 'TND-00003', 'N14 Earthworks — Progress Section 3', 'SANRAL', 'SANRAL-N14-2026-07',
     CURRENT_DATE - INTERVAL '5 days', CURRENT_DATE - INTERVAL '20 days', 12500000, 'Construction', 'cidb Grade 7GB', 'SUBMITTED', sys_user, sys_user);

UPDATE tenders SET submitted_at = NOW() - INTERVAL '5 days' WHERE id = tnd_sub;

INSERT INTO tender_requirements (id, tenant_id, tender_id, compliance_requirement_id, description, source, status, created_by, updated_by)
VALUES
    (gen_random_uuid(), tenant_id, tnd_draft, req2, 'CIDB Grade 6GB or higher', 'COMPLIANCE', 'MET', sys_user, sys_user),
    (gen_random_uuid(), tenant_id, tnd_draft, req3, 'Valid SARS Tax Clearance Certificate', 'COMPLIANCE', 'MET', sys_user, sys_user),
    (gen_random_uuid(), tenant_id, tnd_prep,  req1, 'Valid, active CSD supplier registration', 'COMPLIANCE', 'MISSING', sys_user, sys_user),
    (gen_random_uuid(), tenant_id, tnd_sub,   req2, 'CIDB Grade 6GB or higher', 'COMPLIANCE', 'MET', sys_user, sys_user),
    (gen_random_uuid(), tenant_id, tnd_sub,   req3, 'Valid SARS Tax Clearance Certificate', 'COMPLIANCE', 'MET', sys_user, sys_user),
    (gen_random_uuid(), tenant_id, tnd_sub,   NULL, 'Three references from similar road projects', 'MANUAL', 'MET', sys_user, sys_user);

INSERT INTO tender_personnel (id, tenant_id, tender_id, employee_id, role, created_by)
VALUES
    (gen_random_uuid(), tenant_id, tnd_sub, emp_thabo,  'Project Manager', sys_user),
    (gen_random_uuid(), tenant_id, tnd_sub, emp_naledi, 'Site Agent', sys_user);

INSERT INTO tender_submission_snapshots (id, tenant_id, tender_id, snapshot_number, snapshot_json, submitted_at, submitted_by)
VALUES (gen_random_uuid(), tenant_id, tnd_sub, 1,
    jsonb_build_object(
        'tenderId', tnd_sub, 'tenderNumber', 'TND-00003', 'name', 'N14 Earthworks — Progress Section 3',
        'tenderAuthority', 'SANRAL', 'authorityReferenceNumber', 'SANRAL-N14-2026-07',
        'closingDate', (CURRENT_DATE - INTERVAL '5 days')::date, 'estimatedValue', 12500000,
        'industry', 'Construction', 'requiredClassOfWork', 'cidb Grade 7GB', 'status', 'SUBMITTED',
        'requirements', jsonb_build_array(
            jsonb_build_object('description', 'CIDB Grade 6GB or higher', 'source', 'COMPLIANCE', 'status', 'MET'),
            jsonb_build_object('description', 'Valid SARS Tax Clearance Certificate', 'source', 'COMPLIANCE', 'status', 'MET'),
            jsonb_build_object('description', 'Three references from similar road projects', 'source', 'MANUAL', 'status', 'MET')
        ),
        'personnel', jsonb_build_array(
            jsonb_build_object('employeeId', emp_thabo, 'role', 'Project Manager', 'employeeFullName', 'Thabo Mokoena', 'employeeNumber', 'EMP-0001'),
            jsonb_build_object('employeeId', emp_naledi, 'role', 'Site Agent', 'employeeFullName', 'Naledi Dube', 'employeeNumber', 'EMP-0002')
        ),
        'capturedAt', to_char((NOW() - INTERVAL '5 days') AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
    ),
    NOW() - INTERVAL '5 days', sys_user);

-- ═══════════════════════════════════════════════════════════════
-- complianceservices — Zeta as a compliance service provider for
-- two other, smaller subcontractor companies
-- ═══════════════════════════════════════════════════════════════
INSERT INTO compliance_service_clients (id, tenant_id, name, contact_email, contact_phone, mandate_notes, status, created_by, updated_by)
VALUES
    (client_acme, tenant_id, 'Acme Plant Hire (Pty) Ltd', 'ops@acmeplanthire.co.za', '0119876543',
     'Full compliance management mandate signed 2026-02-01.', 'ACTIVE', sys_user, sys_user),
    (client_tau, tenant_id, 'Tau Civils CC', 'admin@taucivils.co.za', '0126541234',
     'Registration and tender-support mandate — renews annually.', 'ACTIVE', sys_user, sys_user);

INSERT INTO client_compliance_registrations (id, tenant_id, client_id, authority, registration_type,
    registration_number, status, issued_date, expiry_date, notes, created_by, updated_by)
VALUES
    (creg1, tenant_id, client_acme, 'CIPC', 'Business Registration', '2018/778241/07', 'ACTIVE', '2018-05-01', NULL, NULL, sys_user, sys_user),
    (creg2, tenant_id, client_acme, 'CSD',  'Supplier Registration', 'MAAA0987654', 'ACTIVE', '2025-11-01', CURRENT_DATE + INTERVAL '12 days', 'Renewal in progress.', sys_user, sys_user),
    (creg3, tenant_id, client_tau,  'CIDB', 'Grade 4GB Registration', 'CIDB-2B8891', 'ACTIVE', '2025-03-15', '2027-03-15', NULL, sys_user, sys_user);

INSERT INTO evidence (id, tenant_id, file_name, content_type, file_size_bytes, storage_key,
    evidence_type, source_module, related_entity_type, related_entity_id, file_hash, uploaded_by, uploaded_by_name)
VALUES
    (cev_doc1, tenant_id, 'acme-csd-registration.pdf', 'application/pdf', 88213,
     'seed-data/complianceservices/acme-csd-registration.pdf', 'CSD Registration Certificate',
     'complianceservices', 'ClientComplianceDocument', cdoc1, 'seed0000000000000000000000000000000000000000000000000000004', sys_user, 'Seed Script');

INSERT INTO client_compliance_documents (id, tenant_id, client_id, registration_id, document_type, evidence_id,
    issue_date, expiry_date, verified_by, verified_at, created_by, updated_by)
VALUES
    (cdoc1, tenant_id, client_acme, creg2, 'CSD Registration Certificate', cev_doc1,
     '2025-11-01', CURRENT_DATE + INTERVAL '12 days', NULL, NULL, sys_user, sys_user);

INSERT INTO client_compliance_deadlines (id, tenant_id, client_id, registration_id, deadline_type, description, due_date, status, created_by, updated_by)
VALUES
    (cdl1, tenant_id, client_acme, creg2, 'RENEWAL', 'Acme CSD registration renewal', CURRENT_DATE + INTERVAL '12 days', 'PENDING', sys_user, sys_user),
    (cdl2, tenant_id, client_tau,  NULL,  'ANNUAL_RETURN', 'Tau Civils CIPC annual return', CURRENT_DATE + INTERVAL '30 days', 'PENDING', sys_user, sys_user);

INSERT INTO client_compliance_requirements (id, tenant_id, client_id, code, name, applies_to, evidence_type, required, requirement_version, created_by, updated_by)
VALUES
    (creq1, tenant_id, client_acme, 'CSD_ACTIVE', 'Valid, active CSD supplier registration', 'Government Tender', 'CSD Registration Certificate', TRUE, 1, sys_user, sys_user),
    (creq2, tenant_id, client_tau,  'CIDB_GRADE', 'CIDB Grade 4GB or higher', 'Construction', 'cidb Certificate', TRUE, 1, sys_user, sys_user);

INSERT INTO client_tenders (id, tenant_id, client_id, tender_number, name, tender_authority, authority_reference_number,
    closing_date, briefing_date, estimated_value, industry, required_class_of_work, status, created_by, updated_by)
VALUES
    (ctnd_draft, tenant_id, client_tau, 'CTND-00001', 'District Access Road Resealing', 'Limpopo DRPW', 'LDRPW-2026-051',
     CURRENT_DATE + INTERVAL '28 days', CURRENT_DATE + INTERVAL '7 days', 3100000, 'Construction', 'cidb Grade 4GB', 'DRAFT', sys_user, sys_user),
    (ctnd_sub, tenant_id, client_acme, 'CTND-00002', 'Municipal Plant Hire Panel — 2026/2028', 'City of Ekurhuleni', 'COE-2026-093',
     CURRENT_DATE - INTERVAL '3 days', CURRENT_DATE - INTERVAL '15 days', 6800000, 'Plant Hire', NULL, 'SUBMITTED', sys_user, sys_user);

UPDATE client_tenders SET submitted_at = NOW() - INTERVAL '3 days' WHERE id = ctnd_sub;

INSERT INTO client_tender_requirements (id, tenant_id, client_tender_id, client_requirement_id, description, source, status, created_by, updated_by)
VALUES
    (gen_random_uuid(), tenant_id, ctnd_draft, creq2, 'CIDB Grade 4GB or higher', 'COMPLIANCE', 'MET', sys_user, sys_user),
    (gen_random_uuid(), tenant_id, ctnd_sub,   creq1, 'Valid, active CSD supplier registration', 'COMPLIANCE', 'MET', sys_user, sys_user),
    (gen_random_uuid(), tenant_id, ctnd_sub,   NULL,  'Proof of at least 5 plant items owned or leased', 'MANUAL', 'MET', sys_user, sys_user);

INSERT INTO client_tender_personnel (id, tenant_id, client_tender_id, employee_id, role, created_by)
VALUES
    (gen_random_uuid(), tenant_id, ctnd_sub, emp_thabo, 'Account Manager (on behalf of client)', sys_user);

INSERT INTO client_tender_submission_snapshots (id, tenant_id, client_tender_id, snapshot_number, snapshot_json, submitted_at, submitted_by)
VALUES (gen_random_uuid(), tenant_id, ctnd_sub, 1,
    jsonb_build_object(
        'clientTenderId', ctnd_sub, 'clientId', client_acme, 'tenderNumber', 'CTND-00002',
        'name', 'Municipal Plant Hire Panel — 2026/2028', 'tenderAuthority', 'City of Ekurhuleni',
        'authorityReferenceNumber', 'COE-2026-093', 'closingDate', (CURRENT_DATE - INTERVAL '3 days')::date,
        'estimatedValue', 6800000, 'industry', 'Plant Hire', 'requiredClassOfWork', NULL, 'status', 'SUBMITTED',
        'requirements', jsonb_build_array(
            jsonb_build_object('description', 'Valid, active CSD supplier registration', 'source', 'COMPLIANCE', 'status', 'MET'),
            jsonb_build_object('description', 'Proof of at least 5 plant items owned or leased', 'source', 'MANUAL', 'status', 'MET')
        ),
        'personnel', jsonb_build_array(
            jsonb_build_object('employeeId', emp_thabo, 'role', 'Account Manager (on behalf of client)', 'employeeFullName', 'Thabo Mokoena', 'employeeNumber', 'EMP-0001')
        ),
        'capturedAt', to_char((NOW() - INTERVAL '3 days') AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
    ),
    NOW() - INTERVAL '3 days', sys_user);

END $$;

COMMIT;
