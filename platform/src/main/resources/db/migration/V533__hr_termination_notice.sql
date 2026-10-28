-- Termination: how employment ended and when notice was given, so the notice the BCEA requires (section 37) can be set against the
-- notice actually worked, and pay in place of notice worked out when the employer ended it. Both are optional; employees ended
-- before this change simply have neither, and no notice pay is worked out for them.

ALTER TABLE hr_employees ADD COLUMN IF NOT EXISTS end_type VARCHAR(20);
ALTER TABLE hr_employees ADD COLUMN IF NOT EXISTS notice_given_on DATE;
ALTER TABLE hr_employees DROP CONSTRAINT IF EXISTS hr_employees_end_type_check;
ALTER TABLE hr_employees ADD CONSTRAINT hr_employees_end_type_check
    CHECK (end_type IS NULL OR end_type IN ('RESIGNATION','EMPLOYER_NOTICE','SUMMARY_DISMISSAL','CONTRACT_END','MUTUAL','OTHER'));
