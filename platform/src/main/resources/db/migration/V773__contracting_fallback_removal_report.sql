-- The USER_READ / USER_UPDATE compatibility fallback is removed from the contracting controllers in this release.
-- This records, at the moment of the change, which roles relied on the fallback for a contracting capability and do not hold
-- the explicit CONTRACTS_* permission, so each tenant can review and grant it deliberately. Nothing is granted here.
CREATE TABLE IF NOT EXISTS contracting_fallback_removal_report (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_at             TIMESTAMP NOT NULL DEFAULT now(),
    tenant_id          UUID NOT NULL,
    role_id            UUID NOT NULL,
    role_name          VARCHAR(100) NOT NULL,
    users_in_role      INTEGER NOT NULL,
    permission_lost    VARCHAR(100) NOT NULL,
    previously_via     VARCHAR(100) NOT NULL,
    reviewed_at        TIMESTAMP,
    review_decision    VARCHAR(200)
);

INSERT INTO contracting_fallback_removal_report (tenant_id, role_id, role_name, users_in_role, permission_lost, previously_via)
SELECT r.tenant_id, r.id, r.name,
       (SELECT count(*) FROM user_roles ur WHERE ur.role_id = r.id)::int,
       need.perm, need.via
  FROM roles r
  JOIN (VALUES ('CONTRACTS_READ', 'USER_READ'), ('CONTRACTS_READ', 'USER_UPDATE'),
               ('CONTRACTS_MANAGE', 'USER_UPDATE'), ('CONTRACTS_TEMPLATES', 'USER_UPDATE'),
               ('CONTRACTS_SIGN', 'USER_UPDATE'), ('CONTRACTS_ADMIN', 'USER_UPDATE')) AS need(perm, via) ON TRUE
 WHERE EXISTS (SELECT 1 FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
                WHERE rp.role_id = r.id AND p.name = need.via)
   AND NOT EXISTS (SELECT 1 FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
                    WHERE rp.role_id = r.id AND p.name = need.perm)
   -- a role that still holds CONTRACTS_READ keeps read access, so only list the first matching reason per capability
   AND NOT (need.perm = 'CONTRACTS_READ' AND need.via = 'USER_UPDATE'
            AND EXISTS (SELECT 1 FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
                         WHERE rp.role_id = r.id AND p.name = 'USER_READ'));

-- The V771 report view no longer describes a live fallback; keep it for history.
COMMENT ON VIEW contracting_permission_fallback_report IS 'Historical: state when the USER_* fallback was still active (removed in V773).';
