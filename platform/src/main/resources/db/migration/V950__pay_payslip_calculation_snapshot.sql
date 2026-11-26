-- V950: frozen calculation record on each payslip (phase 1, slice 1b; audit D6).
-- What was used to produce the figures: which statutory rules (year, dates, fingerprint of the exact
-- brackets and figures), which formula version, and the inputs the amounts alone do not show.
-- Payslips made before this migration have NULLs here: "not recorded".
ALTER TABLE pay_payslips
    ADD COLUMN rules_effective_from  DATE,
    ADD COLUMN rules_effective_to    DATE,
    ADD COLUMN rules_fingerprint     VARCHAR(64),
    ADD COLUMN formula_version       VARCHAR(40),
    ADD COLUMN input_date_of_birth   DATE,
    ADD COLUMN input_medical_members INTEGER,
    ADD COLUMN input_annual_payroll  NUMERIC(15,2);
