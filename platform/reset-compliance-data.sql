-- ═══════════════════════════════════════════════════════════════════
-- ONE-TIME DATA RESET for seed-compliance-data.sql — run manually via
-- psql, NOT a Flyway migration. Scoped to Zeta Earthmoving.
--
-- Deletes in FK-respecting order: children before parents, matching
-- reset-accounting-data.sql's own convention. Also removes the two
-- hr_employees this seed added as a prerequisite (Thabo Mokoena, EMP-0001;
-- Naledi Dube, EMP-0002) — if you've since built other test data that
-- references those two employees, running this will leave that other
-- data with a dangling employee_id (not a DB-enforced FK anywhere in
-- this codebase's cross-module references, so it won't error, but the
-- reference will silently point at nothing).
-- ═══════════════════════════════════════════════════════════════════

\set tenant_id '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f'

BEGIN;

-- ── compliancetender ─────────────────────────────────────────────────────
DELETE FROM tender_submission_snapshots WHERE tenant_id = :'tenant_id';
DELETE FROM tender_personnel            WHERE tenant_id = :'tenant_id';
DELETE FROM tender_requirements         WHERE tenant_id = :'tenant_id';
DELETE FROM tenders                     WHERE tenant_id = :'tenant_id';
DELETE FROM compliance_documents        WHERE tenant_id = :'tenant_id';
DELETE FROM compliance_deadlines        WHERE tenant_id = :'tenant_id';
DELETE FROM compliance_requirements     WHERE tenant_id = :'tenant_id';
DELETE FROM compliance_registrations    WHERE tenant_id = :'tenant_id';

-- ── complianceservices ───────────────────────────────────────────────────
DELETE FROM client_tender_submission_snapshots WHERE tenant_id = :'tenant_id';
DELETE FROM client_tender_personnel            WHERE tenant_id = :'tenant_id';
DELETE FROM client_tender_requirements         WHERE tenant_id = :'tenant_id';
DELETE FROM client_tenders                     WHERE tenant_id = :'tenant_id';
DELETE FROM client_compliance_documents        WHERE tenant_id = :'tenant_id';
DELETE FROM client_compliance_deadlines        WHERE tenant_id = :'tenant_id';
DELETE FROM client_compliance_requirements     WHERE tenant_id = :'tenant_id';
DELETE FROM client_compliance_registrations    WHERE tenant_id = :'tenant_id';
DELETE FROM compliance_service_clients         WHERE tenant_id = :'tenant_id';

-- ── shared: evidence, tagged by the two modules' own source_module value ──
DELETE FROM evidence
WHERE tenant_id = :'tenant_id'
AND source_module IN ('compliancetender', 'complianceservices');

-- ── prerequisite HR data this seed added ───────────────────────────────────
DELETE FROM hr_employees
WHERE tenant_id = :'tenant_id'
AND employee_number IN ('EMP-0001', 'EMP-0002');

-- Sanity check before committing — should all read 0
SELECT
    (SELECT COUNT(*) FROM compliance_registrations         WHERE tenant_id = :'tenant_id') AS remaining_compliance_registrations,
    (SELECT COUNT(*) FROM tenders                           WHERE tenant_id = :'tenant_id') AS remaining_tenders,
    (SELECT COUNT(*) FROM compliance_service_clients        WHERE tenant_id = :'tenant_id') AS remaining_clients,
    (SELECT COUNT(*) FROM client_tenders                    WHERE tenant_id = :'tenant_id') AS remaining_client_tenders,
    (SELECT COUNT(*) FROM evidence WHERE tenant_id = :'tenant_id'
        AND source_module IN ('compliancetender', 'complianceservices'))                     AS remaining_evidence;

COMMIT;
