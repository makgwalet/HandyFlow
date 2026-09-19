-- src/main/resources/db/migration/V287__permission_read_only_flag.sql
--
-- Part 2 of making admin support impersonation actually work.
--
-- V285/V286 (this same initiative) already fixed the crash in
-- JwtService.extractPermissions — an impersonation token (see
-- AdminAuthService.generateImpersonationToken) has no "permissions" claim
-- at all, which used to throw a NullPointerException. That fix alone
-- wasn't enough to make impersonation USEFUL though: with an empty
-- permission set, every endpoint's @PreAuthorize("hasAuthority(...)")
-- check — including plain reads, confirmed against InvoiceController's own
-- GET endpoints — denies the request. A superadmin using "view as tenant"
-- could authenticate but couldn't see anything.
--
-- This migration is what lets the impersonation token carry a real,
-- explicit set of read-only authorities: a permission is marked
-- is_read_only = true, and AdminAuthService grants every such permission
-- to the impersonation JWT.
--
-- WHY NOT derive this from the permission NAME at runtime (e.g. "ends
-- with _READ")? Checked first: the permissions table's own naming isn't
-- fully consistent — Permission.java's own doc comment gives "REPORT_VIEW"
-- as a real example alongside "USER_READ", and a naming-only heuristic run
-- at token-generation time would silently include or exclude permissions
-- as that catalog keeps growing, with no review step. An explicit column,
-- set once here and adjustable afterward through normal data changes, is
-- the auditable version of the same idea.
--
-- This migration's backfill is still a heuristic, not a guarantee — same
-- honesty as V285's document_code backfill. It marks is_read_only = true
-- for permissions whose name ends in _READ or _VIEW (the two suffixes
-- actually confirmed in this codebase) and leaves everything else false.
-- Recommended before impersonation is used on real tenant data: a
-- superadmin should run
--   SELECT name FROM permissions WHERE is_read_only = true ORDER BY name;
-- and confirm nothing on that list can mutate data (a misnamed permission
-- like a hypothetical "REPORT_VIEW_AND_EXPORT" ending in a read-sounding
-- word but doing more should be caught here, not assumed safe because the
-- name matched a regex).

ALTER TABLE permissions
    ADD COLUMN is_read_only BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN permissions.is_read_only IS
    'Whether this permission only grants read access — used to build the '
    'authority set for admin support impersonation tokens (read-only by '
    'design; see AdminAuthService.generateImpersonationToken). Backfilled '
    'by suffix (_READ / _VIEW) in V287; treat as a starting point requiring '
    'human review before impersonation is relied on for real tenant '
    'support, not as a guarantee.';

UPDATE permissions
SET is_read_only = true
WHERE name ILIKE '%\_READ' ESCAPE '\'
   OR name ILIKE '%\_VIEW' ESCAPE '\';
