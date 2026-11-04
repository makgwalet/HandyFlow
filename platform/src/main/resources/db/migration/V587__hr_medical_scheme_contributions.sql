-- Medical scheme membership: what each side pays, and who is covered, kept on the same dated row so history stays.
-- dependants stays the total people covered besides the employee (the payroll tax credit reads it); the three counts
-- say who they are. Rows that existed before this have no breakdown, so their dependants are shown as "other".
ALTER TABLE hr_medical_scheme_memberships
    ADD COLUMN employee_contribution NUMERIC(12,2) CHECK (employee_contribution IS NULL OR employee_contribution >= 0),
    ADD COLUMN employer_contribution NUMERIC(12,2) CHECK (employer_contribution IS NULL OR employer_contribution >= 0),
    ADD COLUMN spouses               INT NOT NULL DEFAULT 0 CHECK (spouses BETWEEN 0 AND 1),
    ADD COLUMN children              INT NOT NULL DEFAULT 0 CHECK (children BETWEEN 0 AND 20),
    ADD COLUMN other_dependants      INT NOT NULL DEFAULT 0 CHECK (other_dependants BETWEEN 0 AND 20);

UPDATE hr_medical_scheme_memberships SET other_dependants = dependants WHERE dependants > 0;

ALTER TABLE hr_medical_scheme_memberships
    ADD CONSTRAINT chk_hr_medical_dependants_total CHECK (dependants = spouses + children + other_dependants);
