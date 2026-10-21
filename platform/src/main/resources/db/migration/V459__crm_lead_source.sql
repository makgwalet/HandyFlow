-- V459: where a lead or customer came from. Optional; older records stay NULL and are reported as "Not recorded".
ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS lead_source VARCHAR(30);

ALTER TABLE customers
    ADD CONSTRAINT chk_customers_lead_source
        CHECK (lead_source IS NULL OR lead_source IN
               ('WEBSITE', 'REFERRAL', 'WALK_IN', 'PHONE', 'EMAIL', 'SOCIAL_MEDIA', 'ADVERT', 'EVENT', 'PARTNER', 'OTHER'));
