-- V___tenant_registration_metadata.sql
-- (rename to the next available Flyway version number in your sequence)
--
-- Backs businessType and promoCode support on registration. Both fields
-- were already being sent by RegisterPage.tsx (the industry-first picker
-- and promo-code entry, apparently built independently ahead of the
-- backend) but RegisterRequest had no fields to receive them — Jackson
-- silently drops unknown JSON properties, so every registration was
-- dropping both invisibly.
--
-- Deliberately just storage, not validation or discount application.
-- businessType is descriptive metadata — the frontend already computes
-- the real module selection before sending moduleKeys, so nothing here
-- re-derives anything from it. promoCode is recorded as entered, not
-- validated or applied — actually turning a promo code into a real
-- discount needs the PricingEngine/promo-validation service the original
-- analysis recommended, which doesn't exist yet. This stops the silent
-- drop; it doesn't build the rest of that feature.

ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS business_type VARCHAR(50),
    ADD COLUMN IF NOT EXISTS promo_code    VARCHAR(50);