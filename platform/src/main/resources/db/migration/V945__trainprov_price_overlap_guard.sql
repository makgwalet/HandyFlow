-- Training Provider 0179: database guard against overlapping live price lines.
-- The service already refuses a second live line for the same fee in the same period; this makes the database refuse it too, so two people saving at
-- the same moment, or a script, cannot slip one through. Same fee means: same programme version, same funder (or both "any funder"), same fee type,
-- and for OTHER the same description ignoring case. Withdrawn lines are not counted. Dates are inclusive and an empty end date is open-ended.
-- Resource bookings are not guarded this way on purpose: they depend on quantity, shareability and an administrator's written override, which
-- an exclusion constraint cannot express. They stay guarded by the locked check in the service.

CREATE EXTENSION IF NOT EXISTS btree_gist;

DO $$
DECLARE clashes INTEGER;
BEGIN
    SELECT count(*) INTO clashes
    FROM trainprov_price_lines a
    JOIN trainprov_price_lines b ON a.id < b.id
        AND a.tenant_id = b.tenant_id
        AND a.programme_version_id = b.programme_version_id
        AND COALESCE(a.funder_id, '00000000-0000-0000-0000-000000000000'::uuid) = COALESCE(b.funder_id, '00000000-0000-0000-0000-000000000000'::uuid)
        AND a.fee_type = b.fee_type
        AND (a.fee_type <> 'OTHER' OR lower(btrim(a.description)) = lower(btrim(b.description)))
        AND daterange(a.valid_from, a.valid_to, '[]') && daterange(b.valid_from, b.valid_to, '[]')
    WHERE a.status = 'ACTIVE' AND b.status = 'ACTIVE';
    IF clashes > 0 THEN
        RAISE EXCEPTION 'V945: % pair(s) of live price lines already overlap for the same fee. Withdraw one of each pair (with a reason) and run the migration again.', clashes;
    END IF;
END $$;

ALTER TABLE trainprov_price_lines ADD CONSTRAINT ex_trainprov_price_no_overlap EXCLUDE USING gist (
    tenant_id WITH =,
    programme_version_id WITH =,
    (COALESCE(funder_id, '00000000-0000-0000-0000-000000000000'::uuid)) WITH =,
    (CASE WHEN fee_type = 'OTHER' THEN 'OTHER:' || lower(btrim(COALESCE(description, ''))) ELSE fee_type END) WITH =,
    daterange(valid_from, valid_to, '[]') WITH &&
) WHERE (status = 'ACTIVE');
