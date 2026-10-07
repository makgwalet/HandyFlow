-- Key personnel are not always HR employees: directors, subcontractors and consultants are named on tenders too.
-- An external person has no employee_id; the name (and organisation) are recorded on the tender row itself.
ALTER TABLE tender_personnel ALTER COLUMN employee_id DROP NOT NULL;
ALTER TABLE tender_personnel ADD COLUMN person_type VARCHAR(20) NOT NULL DEFAULT 'EMPLOYEE';
ALTER TABLE tender_personnel ADD COLUMN external_name VARCHAR(200);
ALTER TABLE tender_personnel ADD COLUMN external_organisation VARCHAR(200);
ALTER TABLE tender_personnel ADD CONSTRAINT ck_tender_personnel_who CHECK (
    (person_type = 'EMPLOYEE' AND employee_id IS NOT NULL)
    OR (person_type <> 'EMPLOYEE' AND employee_id IS NULL AND external_name IS NOT NULL AND length(trim(external_name)) > 0));
