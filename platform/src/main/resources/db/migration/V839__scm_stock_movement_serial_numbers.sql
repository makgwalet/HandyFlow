-- ScStockMovement maps serial_numbers, but no migration ever added it to sc_stock_movements (V86 put it on sc_gr_lines only).
-- On a fresh database Hibernate's ddl-auto=validate stops startup with "missing column [serial_numbers]".
-- IF NOT EXISTS keeps this harmless on databases that already have the column.
ALTER TABLE sc_stock_movements ADD COLUMN IF NOT EXISTS serial_numbers TEXT;
