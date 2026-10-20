-- V456: why a lead was lost. Recorded when the lead moves to LOST, cleared if it is reopened.
-- Older lost leads have no reason (NULL) and are reported as "No reason recorded".
ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS lost_reason VARCHAR(30),
    ADD COLUMN IF NOT EXISTS lost_note   VARCHAR(500);

ALTER TABLE customers
    ADD CONSTRAINT chk_customers_lost_reason
        CHECK (lost_reason IS NULL OR lost_reason IN
               ('PRICE', 'COMPETITOR', 'NO_RESPONSE', 'NO_BUDGET', 'NOT_A_FIT', 'TIMING', 'OTHER'));
