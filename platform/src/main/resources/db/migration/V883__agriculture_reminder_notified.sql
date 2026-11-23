-- Agriculture chunk 8: telling someone about a reminder is not the same as someone dealing with it.
-- Until now the 09:30 sweep set the "acknowledged" flag after sending its message, and the farm dashboard's attention list only shows
-- unacknowledged items, so every due vaccination and scouting follow-up disappeared from the dashboard the morning it fell due.
-- The sweep now writes its own date (below) and leaves "acknowledged" to people.
ALTER TABLE ag_health_events    ADD COLUMN IF NOT EXISTS reminder_notified_on  DATE;
ALTER TABLE ag_scouting_records ADD COLUMN IF NOT EXISTS follow_up_notified_on DATE;
ALTER TABLE ag_inventory_items  ADD COLUMN IF NOT EXISTS low_stock_notified_on DATE;

-- Rows the old sweep already acknowledged were told about on or after their due date. Record that, so nothing is announced twice.
UPDATE ag_health_events    SET reminder_notified_on  = next_due_date  WHERE reminder_acknowledged  AND next_due_date IS NOT NULL AND reminder_notified_on IS NULL;
UPDATE ag_scouting_records SET follow_up_notified_on = follow_up_date WHERE follow_up_acknowledged AND follow_up_date IS NOT NULL AND follow_up_notified_on IS NULL;

-- Items already low have had a message (every day); the next one is a week from the first new sweep, not today.
UPDATE ag_inventory_items SET low_stock_notified_on = CURRENT_DATE
 WHERE deleted_at IS NULL AND reorder_level IS NOT NULL AND current_quantity < reorder_level AND low_stock_notified_on IS NULL;
