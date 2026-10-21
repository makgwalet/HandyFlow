-- Which part of the business a driver belongs to (Operations, Logistics, ...). Free text, optional.
ALTER TABLE fleet_drivers ADD COLUMN department VARCHAR(100);
