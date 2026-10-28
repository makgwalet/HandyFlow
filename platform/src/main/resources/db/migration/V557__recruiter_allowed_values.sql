-- Two lists of allowed values in the original recruiter tables were shorter than what the application now writes:
--   applications put forward from the talent pool have source TALENT_POOL
--   an interview can be recorded as CANCELLED
-- The old checks are found by what they check, since they were created without names.
DO $$
DECLARE r record;
BEGIN
    FOR r IN SELECT conname FROM pg_constraint
             WHERE conrelid = 'rec_applications'::regclass AND contype = 'c' AND pg_get_constraintdef(oid) LIKE '%source%' LOOP
        EXECUTE format('ALTER TABLE rec_applications DROP CONSTRAINT %I', r.conname);
    END LOOP;
    ALTER TABLE rec_applications ADD CONSTRAINT chk_rec_applications_source
        CHECK (source IS NULL OR source IN ('CAREERS_PAGE','REFERRAL','LINKEDIN','INDEED','MANUAL','OTHER','TALENT_POOL'));

    FOR r IN SELECT conname FROM pg_constraint
             WHERE conrelid = 'rec_interviews'::regclass AND contype = 'c' AND pg_get_constraintdef(oid) LIKE '%outcome%' LOOP
        EXECUTE format('ALTER TABLE rec_interviews DROP CONSTRAINT %I', r.conname);
    END LOOP;
    ALTER TABLE rec_interviews ADD CONSTRAINT chk_rec_interviews_outcome
        CHECK (outcome IS NULL OR outcome IN ('PENDING','PASSED','FAILED','NO_SHOW','RESCHEDULED','CANCELLED'));
END $$;
