-- V___billing_contact_routing.sql
-- (rename to the next available Flyway version number in your sequence)
--
-- Backs dedicated billing-contact routing. Confirmed the current
-- mechanism (TenantAdminRecipientsImpl) sends every billing communication
-- — subscription invoices, payment receipts, past-due notices — to up to
-- 5 active users ordered by created_at, with no distinction for role. A
-- security guard supervisor or a clinic receptionist who happens to be
-- one of the first 5 users created would receive the company's
-- subscription invoice.
--
-- Deliberately NOT added to TenantAdminRecipientsImpl itself — that class
-- is used generically across modules (confirmed: FleetService.
-- updateStatus() calls it for vehicle notifications with nothing to do
-- with billing), so repurposing it for billing-specific routing would
-- break its existing use elsewhere. This is new, separate infrastructure.

ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS billing_email        VARCHAR(255),
    ADD COLUMN IF NOT EXISTS billing_contact_name  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS billing_phone         VARCHAR(50);

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS receives_billing_comms BOOLEAN NOT NULL DEFAULT false;
