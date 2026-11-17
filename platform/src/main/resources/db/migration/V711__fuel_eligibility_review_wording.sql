-- D7: review is separate from override. Nobody can override a block (an expired licence, an unapproved tanker); this permission only lets a person
-- allocate a load whose check could not be made because information is missing, with a reason that is recorded and can be audited.
-- It is assigned to whichever roles a company chooses (for example its fuel managers, dispatch supervisors and compliance officers); no role name is built in.
UPDATE permissions
   SET description = 'Allocate a load when an eligibility check could not be made because information is missing, with a recorded reason. Cannot override a block such as an expired licence'
 WHERE name = 'FUEL_ELIGIBILITY_REVIEW';

-- The log of those decisions is read-only and newest first.
CREATE INDEX IF NOT EXISTS ix_fuel_elig_overrides_recent ON fuel_eligibility_overrides (tenant_id, created_at DESC);
