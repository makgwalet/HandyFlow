-- V952: periodic pay (phase 1, slice 1d).
-- uif_base: the part of this payslip's pay that UIF was charged on. Weekly and fortnightly payrolls share one monthly UIF ceiling
-- across the pay runs of a calendar month, so each payslip records its base and the next run adds them up.
-- input_periods_per_year: 12, 26 or 52 - the annualisation the PAYE was worked out with. NULL on payslips made before this migration.
ALTER TABLE pay_payslips
    ADD COLUMN uif_base               NUMERIC(15,2),
    ADD COLUMN input_periods_per_year INTEGER;
