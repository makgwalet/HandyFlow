-- Contract volume pricing. A contract's linked price may carry a cumulative-volume threshold: the litres delivered under the contract before an order.
-- The price itself is still an ordinary price entry (created and approved as always); this only says from what contract volume it applies.
-- 0 means no threshold, so existing links and contracts without a ladder behave exactly as before.
ALTER TABLE fuel_contract_prices ADD COLUMN from_litres NUMERIC(14,2) NOT NULL DEFAULT 0;
ALTER TABLE fuel_contract_prices ADD CONSTRAINT ck_fuel_contract_prices_from CHECK (from_litres >= 0);
