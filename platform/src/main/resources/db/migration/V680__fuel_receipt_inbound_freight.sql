-- Phase 7b: inbound freight on a stock receipt. The supplier's price and the receipt's total cost stay exactly as the supplier
-- charged them (purchase order matching and supplier invoice matching use those). Freight is kept beside them and is added to the
-- tank's weighted-average cost at receipt, so cost at sale includes what it cost to get the fuel to the depot.
-- Existing receipts have no freight and are unchanged.
ALTER TABLE fuel_receipts ADD COLUMN freight_amount NUMERIC(14,2);
ALTER TABLE fuel_receipts ADD COLUMN freight_note   VARCHAR(200);
ALTER TABLE fuel_receipts ADD CONSTRAINT ck_fuel_receipts_freight CHECK (freight_amount IS NULL OR freight_amount >= 0);
