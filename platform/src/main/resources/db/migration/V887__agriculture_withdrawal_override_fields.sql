-- Agriculture: the reason for going ahead during a withdrawal period or pre-harvest interval gets its own columns,
-- instead of being a line at the end of the notes. Applies after V886 (V885 if V886 is not applied).
-- Idempotent: columns are added IF NOT EXISTS, and the copy only touches rows whose new column is still empty.

ALTER TABLE ag_movement_records ADD COLUMN IF NOT EXISTS withdrawal_override_reason TEXT, ADD COLUMN IF NOT EXISTS withdrawal_held_until DATE;
ALTER TABLE ag_harvest_records  ADD COLUMN IF NOT EXISTS withdrawal_override_reason TEXT, ADD COLUMN IF NOT EXISTS withdrawal_held_until DATE;
ALTER TABLE ag_animals          ADD COLUMN IF NOT EXISTS withdrawal_override_reason TEXT, ADD COLUMN IF NOT EXISTS withdrawal_held_until DATE;

-- Move the old line out of the notes. The old line read:
--   Override of withdrawal period (held until 15 Dec 2026): sold for breeding, not slaughter
--   Override of pre-harvest interval (held until 15 Dec 2026): needed before rain
-- The reason and the day go into the new columns and that line is removed from the notes. If the day cannot be read
-- it is left empty; the reason is always kept. Only the first such line on a row is moved.
DO $$
DECLARE t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['ag_movement_records', 'ag_harvest_records', 'ag_animals'] LOOP
        EXECUTE format($f$
            UPDATE %1$I SET
                withdrawal_override_reason = (regexp_match(notes, 'Override of (?:withdrawal period|pre-harvest interval) \(held until [^)]*\): ([^\n]*)'))[1],
                withdrawal_held_until = CASE WHEN (regexp_match(notes, 'Override of (?:withdrawal period|pre-harvest interval) \(held until ([0-9]{1,2} [A-Za-z]{3} [0-9]{4})\)'))[1] IS NULL THEN NULL
                    ELSE to_date((regexp_match(notes, 'Override of (?:withdrawal period|pre-harvest interval) \(held until ([0-9]{1,2} [A-Za-z]{3} [0-9]{4})\)'))[1], 'FMDD Mon YYYY') END,
                notes = NULLIF(btrim(regexp_replace(notes, '(^|\n)Override of (?:withdrawal period|pre-harvest interval) \(held until [^)]*\): [^\n]*', '', 'n'), E' \n'), '')
            WHERE withdrawal_override_reason IS NULL
              AND notes ~ 'Override of (withdrawal period|pre-harvest interval) \(held until [^)]*\): '
        $f$, t);
    END LOOP;
END $$;
