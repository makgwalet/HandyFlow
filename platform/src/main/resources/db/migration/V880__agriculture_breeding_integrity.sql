-- Agriculture chunk 2: breeding records.
-- breeding_type was VARCHAR(10), too short for the values the web screen sent (ARTIFICIAL_INSEMINATION, EMBRYO_TRANSFER),
-- so those saves failed. Stillborn animals are now counted separately from live births.

ALTER TABLE ag_breeding_records ALTER COLUMN breeding_type TYPE VARCHAR(30);
ALTER TABLE ag_breeding_records ADD COLUMN IF NOT EXISTS stillborn_count INTEGER;

UPDATE ag_breeding_records SET breeding_type = 'AI' WHERE breeding_type = 'ARTIFICIAL_INSEMINATION';

-- NOT VALID: new and changed rows are checked; rows already in the table are left as they are.
ALTER TABLE ag_breeding_records
    ADD CONSTRAINT chk_ag_breeding_records_type
        CHECK (breeding_type IN ('NATURAL', 'AI', 'EMBRYO_TRANSFER')) NOT VALID,
    ADD CONSTRAINT chk_ag_breeding_records_outcome
        CHECK (outcome IN ('PENDING', 'CONFIRMED_PREGNANT', 'NOT_PREGNANT', 'BIRTHED', 'STILLBORN', 'ABORTED', 'FAILED')) NOT VALID,
    ADD CONSTRAINT chk_ag_breeding_records_counts
        CHECK ((offspring_count IS NULL OR offspring_count >= 0) AND (stillborn_count IS NULL OR stillborn_count >= 0)) NOT VALID,
    ADD CONSTRAINT chk_ag_breeding_records_dates
        CHECK (expected_due_date IS NULL OR expected_due_date >= mating_date) NOT VALID;
