-- Warehousing: operating model and industry profile, two separate settings on the warehouse profile (they are not the same question, and neither
-- is the billing policy or the subscription).
--   operating_model: INTERNAL (one owner's own stock), THREE_PL (several independent customers), HYBRID (own stock and customers).
--   industry_profile: GENERAL, PHARMA, FOOD or AGRI. Only sets the tracking new items start with; every item can still be changed.
-- Existing tenants keep what they have today: several clients, nothing preset.
ALTER TABLE whse_profile ADD COLUMN IF NOT EXISTS operating_model VARCHAR(10) NOT NULL DEFAULT 'THREE_PL'
    CHECK (operating_model IN ('INTERNAL', 'THREE_PL', 'HYBRID'));
ALTER TABLE whse_profile ADD COLUMN IF NOT EXISTS industry_profile VARCHAR(10) NOT NULL DEFAULT 'GENERAL'
    CHECK (industry_profile IN ('GENERAL', 'PHARMA', 'FOOD', 'AGRI'));
