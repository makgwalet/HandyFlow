-- Hire charges can be billed by kilometres driven (from the machine's odometer readings), alongside days and hours.
ALTER TABLE earthmoving_hire_charges ALTER COLUMN basis TYPE VARCHAR(12);
ALTER TABLE earthmoving_hire_charges DROP CONSTRAINT IF EXISTS earthmoving_hire_charges_basis_check;
ALTER TABLE earthmoving_hire_charges DROP CONSTRAINT IF EXISTS chk_em_hire_basis;
ALTER TABLE earthmoving_hire_charges ADD CONSTRAINT chk_em_hire_basis CHECK (basis IN ('DAILY', 'HOURLY', 'KILOMETRE'));
