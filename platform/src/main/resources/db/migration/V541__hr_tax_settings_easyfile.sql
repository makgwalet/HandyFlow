-- What SARS asks of the employer in the e@syFile import file that the tax settings did not hold: a contact person and the SIC7 industry code.
-- The company name, address, telephone and email come from the company settings already kept.
ALTER TABLE hr_tax_settings ADD COLUMN IF NOT EXISTS contact_first_name VARCHAR(50);
ALTER TABLE hr_tax_settings ADD COLUMN IF NOT EXISTS contact_surname    VARCHAR(50);
ALTER TABLE hr_tax_settings ADD COLUMN IF NOT EXISTS sic7_code          VARCHAR(5);
