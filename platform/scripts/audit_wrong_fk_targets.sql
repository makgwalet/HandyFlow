-- Finds "who did this" audit columns whose foreign key points at something other than users.
--
--   psql -d <database> -f platform/scripts/audit_wrong_fk_targets.sql
--
-- WHY: these columns hold the logged-in STAFF USER's id (TenantContext.getCurrentUserId()).
-- A foreign key to security_guards or hr_employees rejects that id, and the API reports the
-- constraint violation as a generic HTTP 500 "An unexpected error occurred". This defect
-- shipped in seven Security tables (fixed in V303) after HR had already hit it (V52).
--
-- It reads pg_constraint from a real database, so it sees the FINAL state after every
-- migration (including any that DROP a constraint), unlike a static scan of the SQL files.
--
-- Every row returned needs a human decision: is the column meant to hold a user's id (then
-- the foreign key is a bug), or a different entity's id (then it is correct)? Known-correct
-- exceptions are listed at the bottom, and are filtered out so a clean database prints nothing.
SELECT cl.relname  AS "table",
       a.attname   AS "column",
       cf.relname  AS "references (should usually be users)"
FROM pg_constraint c
JOIN pg_class      cl ON cl.oid = c.conrelid
JOIN pg_namespace  n  ON n.oid  = cl.relnamespace AND n.nspname = current_schema()
JOIN pg_class      cf ON cf.oid = c.confrelid
JOIN pg_attribute  a  ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1]
WHERE c.contype = 'f'
  AND array_length(c.conkey, 1) = 1
  AND a.attname ~ '(_by$|^approver|^reviewer|^actor)'
  AND cf.relname NOT IN ('users', 'admin_users')
  AND (cl.relname, a.attname) NOT IN (
      ('security_resource_custody', 'witnessed_by'),   -- a second GUARD's id, supplied in the request
      ('clinic_lab_results',        'reviewed_by'),    -- the controller resolves the practitioner for the user
      ('acc_workpaper_files',       'superseded_by')   -- points at another workpaper FILE, not a person
  )
ORDER BY 1, 2;
