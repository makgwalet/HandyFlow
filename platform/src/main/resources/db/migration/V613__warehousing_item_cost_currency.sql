-- A unit cost is a number in some currency. Until now the currency was implied, so stock value could silently add
-- rands to dollars. Every item now names its cost currency; existing costs were entered in rands.
ALTER TABLE whse_items ADD COLUMN IF NOT EXISTS unit_cost_currency CHAR(3) NOT NULL DEFAULT 'ZAR';
ALTER TABLE whse_items DROP CONSTRAINT IF EXISTS chk_whse_item_cost_currency;
ALTER TABLE whse_items ADD CONSTRAINT chk_whse_item_cost_currency CHECK (unit_cost_currency ~ '^[A-Z]{3}$');
