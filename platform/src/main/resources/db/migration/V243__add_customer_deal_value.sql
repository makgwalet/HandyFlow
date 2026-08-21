-- backlog 4.2 — CRM deal value / expected close date on the pipeline
-- Both nullable: most customers are never leads at all (CustomerType.CUSTOMER
-- from day one), and even leads may have no estimated value yet at NEW/
-- CONTACTED stage — same "unowned is valid, not an error" reasoning already
-- applied to owner_id in the 4.1 migration (V242).
ALTER TABLE customers ADD COLUMN deal_value NUMERIC(15, 2);
ALTER TABLE customers ADD COLUMN expected_close_date DATE;