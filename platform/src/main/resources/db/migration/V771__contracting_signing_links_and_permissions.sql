-- V771: Contracting, approved decisions (18 Nov 2026).
--  1. Signing links are stored as a hash only. The raw token column is blanked.
--  2. Three new permissions (CONTRACTS_READ, CONTRACTS_SIGN, CONTRACTS_TEMPLATES) with a compatibility grant to existing roles.
--  3. A wrong OTP guess counter, so an OTP is destroyed after 5 wrong attempts.
-- Re-runnable.

-- 1. Signing tokens ----------------------------------------------------------------------------------------------------
ALTER TABLE contract_signing_tokens
    ADD COLUMN IF NOT EXISTS issued_by             UUID,
    ADD COLUMN IF NOT EXISTS delivery_status       VARCHAR(20),
    ADD COLUMN IF NOT EXISTS delivery_attempted_at TIMESTAMP;

-- The raw token is no longer kept. Links already emailed keep working: they are checked by signature and by token_hash.
ALTER TABLE contract_signing_tokens ALTER COLUMN token DROP NOT NULL;
UPDATE contract_signing_tokens SET token = NULL WHERE token IS NOT NULL;

-- One active link per signer. First revoke older duplicates, if any exist, keeping the newest.
UPDATE contract_signing_tokens t
   SET revoked_at = now()
 WHERE t.revoked_at IS NULL AND t.used_at IS NULL
   AND EXISTS (SELECT 1 FROM contract_signing_tokens n
                WHERE n.party_id = t.party_id AND n.revoked_at IS NULL AND n.used_at IS NULL
                  AND (n.created_at > t.created_at OR (n.created_at = t.created_at AND n.id > t.id)));

CREATE UNIQUE INDEX IF NOT EXISTS uq_contract_signing_tokens_one_active_per_party
    ON contract_signing_tokens (party_id) WHERE revoked_at IS NULL AND used_at IS NULL;

-- 3. OTP wrong-guess counter -------------------------------------------------------------------------------------------
ALTER TABLE contract_otp_verifications ADD COLUMN IF NOT EXISTS failed_attempts INT NOT NULL DEFAULT 0;

-- 2. Permissions -------------------------------------------------------------------------------------------------------
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'CONTRACTS_READ',      'View contracts, parties, status and permitted documents'),
    (gen_random_uuid(), 'CONTRACTS_SIGN',      'Sign contracts on behalf of the organisation, where delegated authority permits'),
    (gen_random_uuid(), 'CONTRACTS_TEMPLATES', 'Create, edit, review, publish and archive contract templates')
ON CONFLICT (name) DO NOTHING;

-- Metadata (module_key etc.), as the IAM permission rules require.
UPDATE permissions p
   SET module_key = 'contracting', resource = NULL, verb = v.verb,
       sensitivity = 'SENSITIVE', requires_second_person = false, grantable_by = 'ADMIN'
  FROM (VALUES ('CONTRACTS_READ', 'READ'), ('CONTRACTS_SIGN', 'SIGN'), ('CONTRACTS_TEMPLATES', 'TEMPLATES')) AS v(name, verb)
 WHERE p.name = v.name;

-- Record of every grant made by this migration, so it can be reviewed and reversed.
CREATE TABLE IF NOT EXISTS contracting_permission_migration_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_at          TIMESTAMP NOT NULL DEFAULT now(),
    tenant_id       UUID NOT NULL,
    role_id         UUID NOT NULL,
    role_name       VARCHAR(100) NOT NULL,
    permission_name VARCHAR(100) NOT NULL,
    reason          VARCHAR(200) NOT NULL
);

-- CONTRACTS_READ: everyone who could view contracts before (USER_READ / USER_UPDATE / the existing contract permissions).
WITH ins AS (
    INSERT INTO role_permissions (role_id, permission_id)
    SELECT DISTINCT r.id, np.id
      FROM roles r JOIN permissions np ON np.name = 'CONTRACTS_READ'
     WHERE r.name IN ('ADMIN', 'OWNER')
        OR EXISTS (SELECT 1 FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
                    WHERE rp.role_id = r.id AND p.name IN ('USER_READ', 'USER_UPDATE', 'CONTRACTS_MANAGE', 'CONTRACTS_ADMIN'))
    ON CONFLICT DO NOTHING
    RETURNING role_id, permission_id)
INSERT INTO contracting_permission_migration_log (tenant_id, role_id, role_name, permission_name, reason)
SELECT r.tenant_id, r.id, r.name, p.name, 'could view contracts before (USER_READ, USER_UPDATE or a contract permission)'
  FROM ins JOIN roles r ON r.id = ins.role_id JOIN permissions p ON p.id = ins.permission_id;

-- CONTRACTS_MANAGE (existing permission): roles that could manage contracts through USER_UPDATE.
WITH ins AS (
    INSERT INTO role_permissions (role_id, permission_id)
    SELECT DISTINCT r.id, np.id
      FROM roles r JOIN permissions np ON np.name = 'CONTRACTS_MANAGE'
     WHERE r.name IN ('ADMIN', 'OWNER')
        OR EXISTS (SELECT 1 FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
                    WHERE rp.role_id = r.id AND p.name = 'USER_UPDATE')
    ON CONFLICT DO NOTHING
    RETURNING role_id, permission_id)
INSERT INTO contracting_permission_migration_log (tenant_id, role_id, role_name, permission_name, reason)
SELECT r.tenant_id, r.id, r.name, p.name, 'could manage contracts through USER_UPDATE'
  FROM ins JOIN roles r ON r.id = ins.role_id JOIN permissions p ON p.id = ins.permission_id;

-- CONTRACTS_TEMPLATES: roles that could create templates before (USER_UPDATE or CONTRACTS_MANAGE).
WITH ins AS (
    INSERT INTO role_permissions (role_id, permission_id)
    SELECT DISTINCT r.id, np.id
      FROM roles r JOIN permissions np ON np.name = 'CONTRACTS_TEMPLATES'
     WHERE r.name IN ('ADMIN', 'OWNER')
        OR EXISTS (SELECT 1 FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
                    WHERE rp.role_id = r.id AND p.name IN ('USER_UPDATE', 'CONTRACTS_MANAGE'))
    ON CONFLICT DO NOTHING
    RETURNING role_id, permission_id)
INSERT INTO contracting_permission_migration_log (tenant_id, role_id, role_name, permission_name, reason)
SELECT r.tenant_id, r.id, r.name, p.name, 'could create templates before (USER_UPDATE or CONTRACTS_MANAGE)'
  FROM ins JOIN roles r ON r.id = ins.role_id JOIN permissions p ON p.id = ins.permission_id;

-- CONTRACTS_SIGN and CONTRACTS_ADMIN are NOT handed to every manager. Only ADMIN and OWNER roles (and, for ADMIN, roles that
-- already hold it) get them here. Other roles keep signing and terminating through the temporary USER_UPDATE compatibility
-- check in the controllers until each tenant has reviewed who really needs signing authority.
WITH ins AS (
    INSERT INTO role_permissions (role_id, permission_id)
    SELECT DISTINCT r.id, np.id
      FROM roles r JOIN permissions np ON np.name IN ('CONTRACTS_SIGN', 'CONTRACTS_ADMIN')
     WHERE r.name IN ('ADMIN', 'OWNER')
    ON CONFLICT DO NOTHING
    RETURNING role_id, permission_id)
INSERT INTO contracting_permission_migration_log (tenant_id, role_id, role_name, permission_name, reason)
SELECT r.tenant_id, r.id, r.name, p.name, 'ADMIN or OWNER role'
  FROM ins JOIN roles r ON r.id = ins.role_id JOIN permissions p ON p.id = ins.permission_id;

-- Report for the release that removes the USER_* fallback: which roles still depend on it, how many users are in them, and
-- which tenants have no role holding each new permission.
CREATE OR REPLACE VIEW contracting_permission_fallback_report AS
SELECT r.tenant_id,
       r.id   AS role_id,
       r.name AS role_name,
       (SELECT count(*) FROM user_roles ur WHERE ur.role_id = r.id) AS users_in_role,
       EXISTS (SELECT 1 FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id WHERE rp.role_id = r.id AND p.name = 'USER_READ')   AS relies_on_user_read,
       EXISTS (SELECT 1 FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id WHERE rp.role_id = r.id AND p.name = 'USER_UPDATE') AS relies_on_user_update,
       EXISTS (SELECT 1 FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id WHERE rp.role_id = r.id AND p.name = 'CONTRACTS_READ')      AS has_contracts_read,
       EXISTS (SELECT 1 FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id WHERE rp.role_id = r.id AND p.name = 'CONTRACTS_MANAGE')    AS has_contracts_manage,
       EXISTS (SELECT 1 FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id WHERE rp.role_id = r.id AND p.name = 'CONTRACTS_SIGN')      AS has_contracts_sign,
       EXISTS (SELECT 1 FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id WHERE rp.role_id = r.id AND p.name = 'CONTRACTS_ADMIN')     AS has_contracts_admin,
       EXISTS (SELECT 1 FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id WHERE rp.role_id = r.id AND p.name = 'CONTRACTS_TEMPLATES') AS has_contracts_templates
  FROM roles r;
