-- V214__add_guard_employee_codes.sql
-- (Rename to your actual next migration number before applying.)
--
-- CROSS-MODULE NOTE: this migration touches BOTH security_guards (owned by
-- the security module) AND tenants (owned by the identity module). I don't
-- have visibility into how migrations are organized/owned across modules in
-- this codebase -- flag this to whoever owns migration sequencing/review
-- before merging, in case tenants-table changes are expected to live in a
-- different migration set or need identity-team sign-off.
--
-- Guard employee codes: tenant-scoped prefix + zero-padded sequence number
-- (e.g. Shovalula -> S000001, Fast Response -> FS000001), letting 1000+
-- guards who will never have full HandyFlow accounts log into the guard app
-- with employee code + PIN instead of phone + PIN.
--
-- WHY globally unique, not just per-tenant unique?
-- Login-by-employee-code has the same "resolve identity before we know the
-- tenant" requirement that phone-based login already has (see
-- GuardRepository.findActiveByPhone's own javadoc on this). Two tenants
-- could independently pick/derive the same prefix (e.g. two different
-- "S..."-named companies), so per-tenant uniqueness alone wouldn't let a
-- bare code+PIN resolve to the right guard without an extra "which company"
-- step. Global uniqueness (enforced by the unique index below, with
-- application-level collision retry in GuardService) avoids needing that
-- extra step, matching phone login's existing UX.

ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS code_prefix VARCHAR(10),
    ADD COLUMN IF NOT EXISTS next_guard_code_number INTEGER NOT NULL DEFAULT 1;

COMMENT ON COLUMN tenants.code_prefix IS
    'Employee-code prefix for this tenant''s guards (e.g. "S" for Shovalula, "FS" for Fast Response). Null = GuardService derives one from tenants.name at generation time. No admin UI to set this yet -- currently only settable via direct DB update; a proper Settings-page field is a follow-up once someone owns the identity-module UI change.';
COMMENT ON COLUMN tenants.next_guard_code_number IS
    'Atomic counter claimed via UPDATE ... SET next_guard_code_number = next_guard_code_number + 1 ... RETURNING, same pattern as any other claim-and-increment sequence. Not a Postgres SEQUENCE object because per-tenant sequences would mean one DB sequence per tenant, which does not scale cleanly -- a counter column claimed atomically per-row does.';

ALTER TABLE security_guards
    ADD COLUMN IF NOT EXISTS employee_code VARCHAR(20);

CREATE UNIQUE INDEX IF NOT EXISTS idx_security_guards_employee_code
    ON security_guards (employee_code)
    WHERE employee_code IS NOT NULL;

COMMENT ON COLUMN security_guards.employee_code IS
    'Tenant-prefixed, globally-unique login identifier for guards without a reliable registered phone number. Generated once at guard creation (GuardService.generateEmployeeCode) and never reassigned, even if the guard is later soft-deleted -- see the unique index above, which does not exclude deleted rows, so a retired code can never be reissued to someone else.';