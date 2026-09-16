-- V281__guard_banking_fields.sql
-- Structured banking fields on Guard, closing a confirmed gap: payroll
-- export currently needs manual cross-referencing to actually pay a
-- guard, since no bank details are captured anywhere on this entity.
-- Matches PayrollBureau's own PayEmployee.bankName/bankAccountNumber/
-- bankBranchCode field shape exactly (same South African
-- branch-code convention, not IBAN/SWIFT) -- confirmed that
-- established pattern before inventing a new one. Stored plainly, no
-- masking or encryption -- confirmed PayEmployee's own equivalent
-- fields get no special protection either; this doesn't introduce a
-- new inconsistency, it matches the one already established.

ALTER TABLE security_guards
    ADD COLUMN bank_name           VARCHAR(100),
    ADD COLUMN bank_account_number VARCHAR(50),
    ADD COLUMN bank_branch_code    VARCHAR(20);
