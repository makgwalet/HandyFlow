-- E6 conversion preview. READ-ONLY: only SELECTs. Run on the real database before the migration is applied.
-- Rule (approved 6 Nov 2026): per tenant, the earliest-created ACTIVE holder of role ADMIN (created_at asc, id as
-- tie-breaker) becomes OWNER; the other ACTIVE ADMIN holders become ADMINISTRATOR; inactive/locked holders are never
-- promoted (they get ADMINISTRATOR and stay inactive); a tenant with no active ADMIN holder FAILS the migration.

-- 1. Before-and-after, one row per ADMIN holder
WITH holders AS (
  SELECT u.tenant_id, u.id AS user_id, u.email, u.status, u.created_at,
         row_number() OVER (PARTITION BY u.tenant_id
                            ORDER BY (u.status = 'ACTIVE') DESC, u.created_at ASC, u.id ASC) AS rn
  FROM users u
  JOIN user_roles ur ON ur.user_id = u.id
  JOIN roles r ON r.id = ur.role_id AND r.tenant_id = u.tenant_id AND r.name = 'ADMIN')
SELECT t.slug, h.email, h.status, h.created_at,
       'ADMIN' AS role_before,
       CASE WHEN h.status = 'ACTIVE' AND h.rn = 1 THEN 'OWNER' ELSE 'ADMINISTRATOR' END AS role_after
FROM holders h JOIN tenants t ON t.id = h.tenant_id
ORDER BY t.slug, h.rn;

-- 2. Per-tenant summary and failures
SELECT t.slug,
       count(*) FILTER (WHERE h.status = 'ACTIVE')     AS active_admins,
       count(*) FILTER (WHERE h.status <> 'ACTIVE')    AS inactive_admins,
       CASE WHEN count(*) FILTER (WHERE h.status = 'ACTIVE') = 0 THEN 'FAIL: no eligible Owner' ELSE 'ok' END AS result
FROM tenants t
LEFT JOIN (SELECT u.tenant_id, u.status FROM users u
           JOIN user_roles ur ON ur.user_id = u.id
           JOIN roles r ON r.id = ur.role_id AND r.name = 'ADMIN') h ON h.tenant_id = t.id
GROUP BY t.slug ORDER BY result DESC, t.slug;

-- 3. Totals
SELECT (SELECT count(*) FROM tenants) AS tenants,
       (SELECT count(*) FROM users u JOIN user_roles ur ON ur.user_id = u.id JOIN roles r ON r.id = ur.role_id AND r.name = 'ADMIN') AS admin_holders,
       (SELECT count(DISTINCT u.tenant_id) FROM users u JOIN user_roles ur ON ur.user_id = u.id JOIN roles r ON r.id = ur.role_id AND r.name = 'ADMIN' WHERE u.status = 'ACTIVE') AS tenants_with_owner_candidate;

-- 4. Users holding ADMIN together with another role (the migration must decide what to keep)
SELECT t.slug, u.email, string_agg(r.name, ', ' ORDER BY r.name) AS roles
FROM users u JOIN tenants t ON t.id = u.tenant_id
JOIN user_roles ur ON ur.user_id = u.id JOIN roles r ON r.id = ur.role_id
WHERE u.id IN (SELECT ur2.user_id FROM user_roles ur2 JOIN roles r2 ON r2.id = ur2.role_id WHERE r2.name = 'ADMIN')
GROUP BY t.slug, u.email HAVING count(*) > 1;

-- 5. Custom roles that already contain permissions the new Administrator will not hold (informational)
SELECT t.slug, r.name AS role, count(*) AS restricted_permissions
FROM roles r JOIN tenants t ON t.id = r.tenant_id
JOIN role_permissions rp ON rp.role_id = r.id JOIN permissions p ON p.id = rp.permission_id
WHERE r.name <> 'ADMIN' AND p.requires_second_person
GROUP BY t.slug, r.name ORDER BY t.slug, r.name;
