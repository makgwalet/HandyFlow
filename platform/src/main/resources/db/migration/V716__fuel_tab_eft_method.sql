-- Truck stop tabs settled at the till can be paid by EFT as well as cash, card or other.
ALTER TABLE fuel_truck_stop_tabs DROP CONSTRAINT IF EXISTS ck_fuel_ts_tab_method;
ALTER TABLE fuel_truck_stop_tabs ADD CONSTRAINT ck_fuel_ts_tab_method
    CHECK (payment_method IS NULL OR payment_method IN ('CASH','CARD','EFT','OTHER','ACCOUNT'));
