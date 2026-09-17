-- V283__accountant_address_fields.sql
-- Closes a confirmed gap: AccFeeNotePdfGenerator's own comment already
-- flagged this explicitly rather than silently papering over it --
-- "No address block for either party -- confirmed neither
-- AccountantProfile nor AccClient has an address field." A physical
-- business address is required on SARS correspondence and standard
-- accounting-practice invoices; this closes the gap on both entities
-- that need one.
--
-- Flat columns, matching ScSupplier's own established address-field
-- convention in this codebase (street/suburb/city/province/postalCode)
-- rather than a JSONB map -- confirmed that pattern before reusing it.

ALTER TABLE accountant_profiles
    ADD COLUMN address_street      VARCHAR(200),
    ADD COLUMN address_suburb      VARCHAR(100),
    ADD COLUMN address_city        VARCHAR(100),
    ADD COLUMN address_province    VARCHAR(50),
    ADD COLUMN address_postal_code VARCHAR(10);

ALTER TABLE acc_clients
    ADD COLUMN address_street      VARCHAR(200),
    ADD COLUMN address_suburb      VARCHAR(100),
    ADD COLUMN address_city        VARCHAR(100),
    ADD COLUMN address_province    VARCHAR(50),
    ADD COLUMN address_postal_code VARCHAR(10);
