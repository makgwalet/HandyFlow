\set ON_ERROR_STOP on
-- Volume check for the scope, SLA and region queries. Builds a big company in a transaction, times each query on real PostgreSQL and
-- FAILS (non-zero exit with psql -v ON_ERROR_STOP=1) if any query is over its budget. Ends in ROLLBACK, so nothing is kept.
-- Run from the repository root against a scratch database that has the full schema (V866 and V868 applied or not; V868 is applied inside the run):
--   psql -v ON_ERROR_STOP=1 -d scratch -f platform/scripts/db-volume-check.sql   (exit code 3 = a query is over budget; needs a superuser)
-- Budgets are in milliseconds, worst of 5 runs after a warm-up, on the machine you run it on. Change them to what your hardware should do.
BEGIN;
SET LOCAL session_replication_role = replica;   -- bulk-load without foreign-key and trigger cost; superusers only
CREATE TEMP TABLE vol_result(id text, label text, budget_ms numeric, worst_ms numeric);
INSERT INTO tenants (id, tenant_id, name, slug, email)
SELECT ('b1000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid, ('b1000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid, 'Vol ' || n, 'vol' || n, 'v' || n || '@x.co' FROM generate_series(1, 21) n;
\i platform/src/main/resources/db/migration/V868__security_regions.sql
-- the big company (tenant 1): 10 regions, 100 branches, 5,000 sites, 400,000 incidents (1% still open), 300 users (200 limited)
INSERT INTO security_regions (id, tenant_id, name) SELECT ('e1000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid, 'b1000000-0000-0000-0000-000000000001', 'Region ' || n FROM generate_series(1, 10) n;
INSERT INTO security_branches (id, tenant_id, name, region_id)
SELECT ('d1000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid, 'b1000000-0000-0000-0000-000000000001', 'Branch ' || n, ('e1000000-0000-0000-0000-' || lpad(((n - 1) / 10 + 1)::text, 12, '0'))::uuid FROM generate_series(1, 100) n;
INSERT INTO security_sites (id, tenant_id, name, branch_id)
SELECT ('f1000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid, 'b1000000-0000-0000-0000-000000000001', 'Site ' || n, ('d1000000-0000-0000-0000-' || lpad(((n - 1) / 50 + 1)::text, 12, '0'))::uuid FROM generate_series(1, 5000) n;
-- noise: 20 other companies with 50 sites and 20,000 incidents each
INSERT INTO security_sites (id, tenant_id, name)
SELECT ('f2000000-' || lpad(t::text, 4, '0') || '-0000-0000-' || lpad(n::text, 12, '0'))::uuid, ('b1000000-0000-0000-0000-' || lpad((t + 1)::text, 12, '0'))::uuid, 'Site ' || n FROM generate_series(1, 20) t, generate_series(1, 50) n;
INSERT INTO security_incidents (tenant_id, site_id, guard_id, description, occurred_at, severity, status, title, created_at, resolved_at)
SELECT 'b1000000-0000-0000-0000-000000000001', ('f1000000-0000-0000-0000-' || lpad((1 + (n % 5000))::text, 12, '0'))::uuid, gen_random_uuid(), 'x', now() - (n % 365) * interval '1 day',
       (ARRAY['LOW','MEDIUM','HIGH','CRITICAL'])[1 + n % 4], CASE WHEN n % 100 = 0 THEN 'OPEN' ELSE 'RESOLVED' END, 'Incident ' || n,
       now() - (n % 365) * interval '1 day' - (n % 1440) * interval '1 minute', CASE WHEN n % 100 = 0 THEN NULL ELSE now() - (n % 365) * interval '1 day' END
FROM generate_series(1, 400000) n;
INSERT INTO security_incidents (tenant_id, site_id, guard_id, description, occurred_at, severity, status, title, created_at, resolved_at)
SELECT ('b1000000-0000-0000-0000-' || lpad((t + 1)::text, 12, '0'))::uuid, ('f2000000-' || lpad(t::text, 4, '0') || '-0000-0000-' || lpad((1 + n % 50)::text, 12, '0'))::uuid, gen_random_uuid(), 'x',
       now() - (n % 365) * interval '1 day', 'LOW', 'RESOLVED', 'Incident ' || n, now() - (n % 365) * interval '1 day' - (n % 1440) * interval '1 minute', now()
FROM generate_series(1, 20) t, generate_series(1, 20000) n;
INSERT INTO users (id, tenant_id, email, first_name, last_name, password_hash, status)
SELECT ('c1000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid, 'b1000000-0000-0000-0000-000000000001', 'user' || n || '@big.co.za', 'U', 'N' || n, 'x', 'ACTIVE' FROM generate_series(1, 300) n;
-- users 1..200 are limited: user 1 holds one region; user 2 holds five; the rest hold a region, three branches and five named sites
INSERT INTO security_user_regions (tenant_id, user_id, region_id) VALUES
 ('b1000000-0000-0000-0000-000000000001', 'c1000000-0000-0000-0000-000000000001', 'e1000000-0000-0000-0000-000000000001');
INSERT INTO security_user_regions (tenant_id, user_id, region_id) SELECT 'b1000000-0000-0000-0000-000000000001', 'c1000000-0000-0000-0000-000000000002', ('e1000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid FROM generate_series(1, 5) n;
INSERT INTO security_user_regions (tenant_id, user_id, region_id) SELECT 'b1000000-0000-0000-0000-000000000001', ('c1000000-0000-0000-0000-' || lpad(u::text, 12, '0'))::uuid, ('e1000000-0000-0000-0000-' || lpad((1 + u % 10)::text, 12, '0'))::uuid FROM generate_series(3, 200) u;
INSERT INTO security_branch_assignments (tenant_id, branch_id, entity_type, entity_id, role, active)
SELECT 'b1000000-0000-0000-0000-000000000001', ('d1000000-0000-0000-0000-' || lpad((1 + (u * 3 + k) % 100)::text, 12, '0'))::uuid, 'USER', ('c1000000-0000-0000-0000-' || lpad(u::text, 12, '0'))::uuid, 'VIEWER', true FROM generate_series(3, 200) u, generate_series(1, 3) k;
INSERT INTO security_user_scopes (tenant_id, user_id, scope_type, scope_id)
SELECT 'b1000000-0000-0000-0000-000000000001', ('c1000000-0000-0000-0000-' || lpad(u::text, 12, '0'))::uuid, 'SITE', ('f1000000-0000-0000-0000-' || lpad((1 + (u * 7 + k) % 5000)::text, 12, '0'))::uuid FROM generate_series(3, 200) u, generate_series(1, 5) k;
ANALYZE;
CREATE FUNCTION pg_temp.vol_time(id text, label text, budget numeric, q text) RETURNS void LANGUAGE plpgsql AS $f$
DECLARE t0 timestamptz; worst numeric := 0; ms numeric; i int; n bigint;
BEGIN
    EXECUTE 'SELECT count(*) + 0 * coalesce(sum(length(z::text)), 0) FROM (' || q || ') z' INTO n;   -- warm-up
    FOR i IN 1..5 LOOP
        t0 := clock_timestamp(); EXECUTE 'SELECT count(*) + 0 * coalesce(sum(length(z::text)), 0) FROM (' || q || ') z' INTO n;
        ms := extract(epoch FROM clock_timestamp() - t0) * 1000; IF ms > worst THEN worst := ms; END IF;
    END LOOP;
    INSERT INTO vol_result VALUES (id, label || ' [' || n || ' rows]', budget, round(worst, 1));
END $f$;
SELECT pg_temp.vol_time('Q1', $l$has-scope (every request of a limited user)$l$, 5, $q$SELECT (EXISTS (SELECT 1 FROM security_branch_assignments WHERE tenant_id = 'b1000000-0000-0000-0000-000000000001' AND entity_type = 'USER' AND entity_id = 'c1000000-0000-0000-0000-000000000001' AND active
                            AND role IN ('MANAGER', 'VIEWER'))
                 OR EXISTS (SELECT 1 FROM security_user_scopes WHERE tenant_id = 'b1000000-0000-0000-0000-000000000001' AND user_id = 'c1000000-0000-0000-0000-000000000001')
                 OR EXISTS (SELECT 1 FROM security_user_regions WHERE tenant_id = 'b1000000-0000-0000-0000-000000000001' AND user_id = 'c1000000-0000-0000-0000-000000000001'))$q$);
SELECT pg_temp.vol_time('Q2', $l$allowed sites, user limited to 1 region (500 sites)$l$, 30, $q$SELECT s.id FROM security_sites s
            WHERE s.tenant_id = 'b1000000-0000-0000-0000-000000000001' AND s.deleted_at IS NULL AND (
                  s.branch_id IN (SELECT branch_id FROM security_branch_assignments
                                  WHERE tenant_id = 'b1000000-0000-0000-0000-000000000001' AND entity_type = 'USER' AND entity_id = 'c1000000-0000-0000-0000-000000000001' AND active AND role IN ('MANAGER', 'VIEWER'))
               OR s.id IN (SELECT scope_id FROM security_user_scopes WHERE tenant_id = 'b1000000-0000-0000-0000-000000000001' AND user_id = 'c1000000-0000-0000-0000-000000000001' AND scope_type = 'SITE')
               OR s.customer_id IN (SELECT scope_id FROM security_user_scopes WHERE tenant_id = 'b1000000-0000-0000-0000-000000000001' AND user_id = 'c1000000-0000-0000-0000-000000000001' AND scope_type = 'CLIENT')
               OR s.branch_id IN (SELECT b.id FROM security_branches b WHERE b.tenant_id = 'b1000000-0000-0000-0000-000000000001' AND b.region_id IN
                                  (SELECT r.region_id FROM security_user_regions r WHERE r.tenant_id = b.tenant_id AND r.user_id = 'c1000000-0000-0000-0000-000000000001')))$q$);
SELECT pg_temp.vol_time('Q3', $l$allowed sites, user limited to 5 regions (2,500 sites)$l$, 60, $q$SELECT s.id FROM security_sites s
            WHERE s.tenant_id = 'b1000000-0000-0000-0000-000000000001' AND s.deleted_at IS NULL AND (
                  s.branch_id IN (SELECT branch_id FROM security_branch_assignments
                                  WHERE tenant_id = 'b1000000-0000-0000-0000-000000000001' AND entity_type = 'USER' AND entity_id = 'c1000000-0000-0000-0000-000000000002' AND active AND role IN ('MANAGER', 'VIEWER'))
               OR s.id IN (SELECT scope_id FROM security_user_scopes WHERE tenant_id = 'b1000000-0000-0000-0000-000000000001' AND user_id = 'c1000000-0000-0000-0000-000000000002' AND scope_type = 'SITE')
               OR s.customer_id IN (SELECT scope_id FROM security_user_scopes WHERE tenant_id = 'b1000000-0000-0000-0000-000000000001' AND user_id = 'c1000000-0000-0000-0000-000000000002' AND scope_type = 'CLIENT')
               OR s.branch_id IN (SELECT b.id FROM security_branches b WHERE b.tenant_id = 'b1000000-0000-0000-0000-000000000001' AND b.region_id IN
                                  (SELECT r.region_id FROM security_user_regions r WHERE r.tenant_id = b.tenant_id AND r.user_id = 'c1000000-0000-0000-0000-000000000002')))$q$);
SELECT pg_temp.vol_time('Q4', $l$limited-users overview, 300 users, 5,000 sites$l$, 1000, $q$SELECT u.id, u.first_name, u.last_name, u.email, u.status,
                   (SELECT COUNT(*) FROM security_branch_assignments a WHERE a.tenant_id = u.tenant_id AND a.entity_type = 'USER' AND a.entity_id = u.id
                        AND a.active AND a.role IN ('MANAGER', 'VIEWER')) AS branches,
                   (SELECT COUNT(*) FROM security_user_regions g WHERE g.tenant_id = u.tenant_id AND g.user_id = u.id) AS regions,
                   (SELECT COUNT(*) FROM security_user_scopes x WHERE x.tenant_id = u.tenant_id AND x.user_id = u.id AND x.scope_type = 'CLIENT') AS clients,
                   (SELECT COUNT(*) FROM security_user_scopes x WHERE x.tenant_id = u.tenant_id AND x.user_id = u.id AND x.scope_type = 'SITE') AS sites,
                   (SELECT COUNT(*) FROM security_sites s WHERE s.tenant_id = u.tenant_id AND s.deleted_at IS NULL AND (
                        s.branch_id IN (SELECT a.branch_id FROM security_branch_assignments a WHERE a.tenant_id = u.tenant_id AND a.entity_type = 'USER'
                                        AND a.entity_id = u.id AND a.active AND a.role IN ('MANAGER', 'VIEWER'))
                     OR s.branch_id IN (SELECT b.id FROM security_branches b WHERE b.tenant_id = u.tenant_id AND b.region_id IN
                                        (SELECT g.region_id FROM security_user_regions g WHERE g.tenant_id = u.tenant_id AND g.user_id = u.id))
                     OR s.id IN (SELECT x.scope_id FROM security_user_scopes x WHERE x.tenant_id = u.tenant_id AND x.user_id = u.id AND x.scope_type = 'SITE')
                     OR s.customer_id IN (SELECT x.scope_id FROM security_user_scopes x WHERE x.tenant_id = u.tenant_id AND x.user_id = u.id AND x.scope_type = 'CLIENT'))) AS visible_sites
            FROM users u
            WHERE u.tenant_id = 'b1000000-0000-0000-0000-000000000001' AND (
                  EXISTS (SELECT 1 FROM security_branch_assignments a WHERE a.tenant_id = u.tenant_id AND a.entity_type = 'USER' AND a.entity_id = u.id
                          AND a.active AND a.role IN ('MANAGER', 'VIEWER'))
               OR EXISTS (SELECT 1 FROM security_user_scopes x WHERE x.tenant_id = u.tenant_id AND x.user_id = u.id)
               OR EXISTS (SELECT 1 FROM security_user_regions g WHERE g.tenant_id = u.tenant_id AND g.user_id = u.id))
            ORDER BY u.last_name, u.first_name, u.id$q$);
SELECT pg_temp.vol_time('Q5', $l$SLA live: open incidents of the big company$l$, 60, $q$SELECT i.id, i.site_id, s.name, i.severity, i.title, i.status, i.created_at, i.acknowledged_at, i.resolved_at
            FROM security_incidents i JOIN security_sites s ON s.id = i.site_id AND s.tenant_id = i.tenant_id
            WHERE i.tenant_id = 'b1000000-0000-0000-0000-000000000001' AND i.deleted_at IS NULL AND i.resolved_at IS NULL ORDER BY i.created_at LIMIT 5001$q$);
SELECT pg_temp.vol_time('Q6', $l$SLA report: one month of the big company$l$, 300, $q$SELECT i.id, i.site_id, s.name, i.severity, i.title, i.status, i.created_at, i.acknowledged_at, i.resolved_at
            FROM security_incidents i JOIN security_sites s ON s.id = i.site_id AND s.tenant_id = i.tenant_id
            WHERE i.tenant_id = 'b1000000-0000-0000-0000-000000000001' AND i.deleted_at IS NULL AND i.created_at >= '2026-10-01' AND i.created_at < '2026-11-01' ORDER BY i.created_at LIMIT 20001$q$);
SELECT pg_temp.vol_time('Q7', $l$regions list: branches and regions$l$, 20, $q$SELECT region_id, id FROM security_branches WHERE tenant_id = 'b1000000-0000-0000-0000-000000000001' AND region_id IS NOT NULL ORDER BY name$q$);
SELECT pg_temp.vol_time('Q8', $l$regions list: users per region$l$, 20, $q$SELECT r.id, r.name, r.description, (SELECT COUNT(*) FROM security_user_regions x WHERE x.tenant_id = r.tenant_id AND x.region_id = r.id) AS users FROM security_regions r WHERE r.tenant_id = 'b1000000-0000-0000-0000-000000000001' ORDER BY lower(r.name), r.id$q$);
\pset format aligned
SELECT id, label, budget_ms, worst_ms, CASE WHEN worst_ms <= budget_ms THEN 'PASS' ELSE 'FAIL' END AS result FROM vol_result ORDER BY id;
-- a failing query makes the whole run fail, so a script or CI notices
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM vol_result WHERE worst_ms > budget_ms) THEN RAISE EXCEPTION 'volume check: % query(ies) over budget', (SELECT count(*) FROM vol_result WHERE worst_ms > budget_ms); END IF;
END $$;
ROLLBACK;
