-- V9__tenant_company_details.sql
-- WHY IF NOT EXISTS? Some columns (email) were already added in V2.
-- This migration adds only the NEW columns needed for PDF invoice generation.

ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS vat_number    VARCHAR(20),
    ADD COLUMN IF NOT EXISTS phone         VARCHAR(50),
    ADD COLUMN IF NOT EXISTS address       JSONB,
    ADD COLUMN IF NOT EXISTS logo_url      VARCHAR(500),
    ADD COLUMN IF NOT EXISTS bank_name     VARCHAR(100),
    ADD COLUMN IF NOT EXISTS bank_account  VARCHAR(50),
    ADD COLUMN IF NOT EXISTS bank_branch   VARCHAR(20),
    ADD COLUMN IF NOT EXISTS payment_terms TEXT DEFAULT 'Payment due within 30 days of invoice date.';

-- Seed Zeta Earthmoving's company details for PDF testing
UPDATE tenants
SET
    vat_number    = '4560123456',
    phone         = '+27 11 555 0200',
    address       = '{"street":"12 Mining Road","suburb":"Germiston","city":"Johannesburg","province":"Gauteng","postalCode":"1401"}',
    bank_name     = 'First National Bank',
    bank_account  = '62012345678',
    bank_branch   = '632005',
    payment_terms = 'Payment due within 30 days of invoice date. EFT payments only.'
WHERE slug = 'zeta-earthmoving';