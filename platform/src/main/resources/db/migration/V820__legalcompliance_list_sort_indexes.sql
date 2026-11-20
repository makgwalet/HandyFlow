-- V820: indexes that match the sort order of the Legal / Compliance list screens. Confirm the next free Flyway version.
--
-- List screens now read at most 500 rows (RowLimit.MAX_ROWS). With only (tenant_id, status) indexes, Postgres still has
-- to find every matching row and sort them before it can take the first 500. These indexes are ordered the way the
-- screens sort, so it can stop after 500. Incidents already have one (idx_lc_incident_tenant).
-- Partial indexes mirror the default screens, which hide closed / finished items.

CREATE INDEX IF NOT EXISTS idx_lc_finding_list ON legalcompliance_findings (tenant_id, due_date, reference) WHERE status <> 'CLOSED';
CREATE INDEX IF NOT EXISTS idx_lc_afinding_list ON legalcompliance_audit_findings (tenant_id, due_date, reference) WHERE status <> 'CLOSED';
CREATE INDEX IF NOT EXISTS idx_lc_capa_list ON legalcompliance_capas (tenant_id, due_date, reference) WHERE status NOT IN ('VERIFIED', 'CANCELLED');
CREATE INDEX IF NOT EXISTS idx_lc_test_list ON legalcompliance_control_tests (tenant_id, planned_date DESC, reference DESC);
CREATE INDEX IF NOT EXISTS idx_lc_coi_list ON legalcompliance_coi_declarations (tenant_id, declared_on DESC);
CREATE INDEX IF NOT EXISTS idx_lc_gift_list ON legalcompliance_gift_entries (tenant_id, entry_date DESC);
CREATE INDEX IF NOT EXISTS idx_lc_paia_list ON legalcompliance_paia_requests (tenant_id, received_date DESC);
CREATE INDEX IF NOT EXISTS idx_lc_risk_list ON legalcompliance_risks (tenant_id, reference);
