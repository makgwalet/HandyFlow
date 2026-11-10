-- V621: Allocation of a line across several locations, and backorders.
--
-- A line of an item that is not lot tracked used to need one location holding all of it. It can now be split across locations: each piece is
-- a row in whse_order_lot_allocations with no lot. A line may also be started with only part of its quantity available; the rest is recorded
-- as backordered (it counts as short, so it does not ship, but the line remembers it was never allocated rather than found missing).

ALTER TABLE whse_order_lot_allocations ALTER COLUMN lot_id DROP NOT NULL;

ALTER TABLE whse_outbound_order_lines ADD COLUMN qty_backordered NUMERIC(12,3) NOT NULL DEFAULT 0;
ALTER TABLE whse_outbound_order_lines ADD CONSTRAINT chk_whse_line_backordered CHECK (qty_backordered >= 0 AND qty_backordered <= qty_short);
