-- src/main/resources/db/migration/V301__activate_internal_audit_for_zeta.sql
--
-- FIX: found while adding a dashboard tile for internal-audit. V273's own
-- comment on the module_catalogue insert, and V277's seed-data comment,
-- both describe following "V40's own tenant_modules grant" pattern — but
-- neither migration actually contains the INSERT INTO tenant_modules
-- statement. The intent was documented; the code was never written.
--
-- Same consequence as the identical gap just found and fixed for
-- compliancetender/complianceservices in V300: without a tenant_modules
-- row, FeatureGuard.requireModule("internal-audit") throws 403 on every
-- endpoint. internal-audit has never actually been reachable for any
-- tenant — not from its own frontend page (already built, already
-- routed at /internal-audit, just never linked from the dashboard),
-- and not even directly by URL, since every request would have been
-- rejected before reaching the controller.

INSERT INTO tenant_modules (tenant_id, module_key, status, trial_ends_at)
SELECT t.id, 'internal-audit', 'TRIAL', NOW() + INTERVAL '60 days'
FROM tenants t WHERE t.slug = 'zeta-earthmoving'
ON CONFLICT (tenant_id, module_key) DO NOTHING;
