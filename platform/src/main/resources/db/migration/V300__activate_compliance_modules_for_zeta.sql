-- src/main/resources/db/migration/V300__activate_compliance_modules_for_zeta.sql
--
-- FIX: found while preparing seed data for compliancetender and
-- complianceservices — every other module's own creation migration
-- (V35 creative, V36 desk, V37 tasks, etc.) activates itself for the
-- zeta-earthmoving pilot tenant via an INSERT INTO tenant_modules in
-- that same migration. V288 (compliancetender) and V292
-- (complianceservices) never did this. Without a tenant_modules row,
-- FeatureGuard.requireModule() throws 403 Forbidden on every endpoint
-- in both modules — meaning neither has ever actually been reachable
-- for any tenant, including in this dev environment, since the day
-- each was built. Backfilling here, same "Add to zeta-earthmoving
-- pilot" pattern every other module uses.

INSERT INTO tenant_modules (tenant_id, module_key, status, trial_ends_at)
SELECT t.id, 'compliancetender', 'TRIAL', NOW() + INTERVAL '60 days'
FROM tenants t WHERE t.slug = 'zeta-earthmoving'
ON CONFLICT (tenant_id, module_key) DO NOTHING;

INSERT INTO tenant_modules (tenant_id, module_key, status, trial_ends_at)
SELECT t.id, 'complianceservices', 'TRIAL', NOW() + INTERVAL '60 days'
FROM tenants t WHERE t.slug = 'zeta-earthmoving'
ON CONFLICT (tenant_id, module_key) DO NOTHING;
