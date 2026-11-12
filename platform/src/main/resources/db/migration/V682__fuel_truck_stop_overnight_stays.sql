-- Phase 7d: multi-night truck stop parking, owned by Fuel. Bookings holds a slot within one day, so overnight stays are a Fuel model
-- beside the timed Bookings services. The nightly rate and the number of parking bays are the company's own settings; each stay keeps
-- the rate it was booked at, so changing the rate later does not rewrite a stay that already exists.
CREATE TABLE fuel_truck_stop_parking_settings (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    nightly_rate  NUMERIC(10,2),
    bays          INTEGER,
    updated_by    UUID,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    version       BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_fuel_ts_parking_rate CHECK (nightly_rate IS NULL OR nightly_rate >= 0),
    CONSTRAINT ck_fuel_ts_parking_bays CHECK (bays IS NULL OR bays > 0)
);
CREATE UNIQUE INDEX uq_fuel_ts_parking_settings ON fuel_truck_stop_parking_settings (tenant_id);

CREATE TABLE fuel_truck_stop_stays (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    vehicle_registration  VARCHAR(20) NOT NULL,
    driver_name           VARCHAR(120),
    driver_phone          VARCHAR(40),
    arrival_date          DATE NOT NULL,
    departure_date        DATE NOT NULL,
    actual_departure_date DATE,
    status                VARCHAR(12) NOT NULL DEFAULT 'BOOKED',
    rate_per_night        NUMERIC(10,2) NOT NULL,
    note                  VARCHAR(300),
    cancelled_reason      VARCHAR(300),
    checked_in_at         TIMESTAMPTZ,
    checked_out_at        TIMESTAMPTZ,
    created_by            UUID,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_fuel_ts_stay_dates CHECK (departure_date > arrival_date),
    CONSTRAINT ck_fuel_ts_stay_status CHECK (status IN ('BOOKED','CHECKED_IN','CHECKED_OUT','CANCELLED','NO_SHOW')),
    CONSTRAINT ck_fuel_ts_stay_rate CHECK (rate_per_night >= 0),
    CONSTRAINT ck_fuel_ts_stay_out CHECK (status <> 'CHECKED_OUT' OR (actual_departure_date IS NOT NULL AND checked_out_at IS NOT NULL AND actual_departure_date >= arrival_date)),
    CONSTRAINT ck_fuel_ts_stay_cancel CHECK (status NOT IN ('CANCELLED','NO_SHOW') OR cancelled_reason IS NOT NULL)
);
CREATE INDEX ix_fuel_ts_stays_dates ON fuel_truck_stop_stays (tenant_id, arrival_date, departure_date);
CREATE INDEX ix_fuel_ts_stays_vehicle ON fuel_truck_stop_stays (tenant_id, vehicle_registration);
