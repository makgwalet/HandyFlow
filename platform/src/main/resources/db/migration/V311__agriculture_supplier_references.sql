-- V311__agriculture_supplier_references.sql
--
-- ADR-001, W7: Agriculture refers to Supply Chain's suppliers instead of typing names.
--   ag_stock_movements.supplier_id  the Supply Chain supplier a RECEIPT was bought from (null = none recorded)
--   ag_inventory_items.supplier_id  the item's usual supplier; a receipt with no supplier of its own takes this one
-- References by id only, deliberately WITHOUT a foreign key: Supply Chain owns suppliers and Agriculture must not depend on its tables. A supplier that is later removed
-- leaves the id behind, and reports show such a receipt as "Unknown supplier (removed)". The free-text ag_inventory_items.supplier column stays, so history is not lost.
ALTER TABLE ag_stock_movements ADD COLUMN supplier_id UUID;
ALTER TABLE ag_inventory_items ADD COLUMN supplier_id UUID;

CREATE INDEX ix_ag_stock_movements_supplier ON ag_stock_movements (tenant_id, supplier_id) WHERE supplier_id IS NOT NULL;
