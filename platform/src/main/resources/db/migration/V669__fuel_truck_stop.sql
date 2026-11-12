-- E01: Truck Stop & Driver Services. Reservations live in Bookings (owner decision); Fuel only says which Bookings services
-- the truck stop sells and keeps the truck and driver details Bookings has no place for. No foreign keys into Bookings tables.
CREATE TABLE fuel_truck_stop_offerings (
    id                  UUID         PRIMARY KEY,
    tenant_id           UUID         NOT NULL,
    bookings_service_id UUID         NOT NULL,
    kind                VARCHAR(12)  NOT NULL CHECK (kind IN ('PARKING','SHOWER','WASH','WEIGHBRIDGE','WORKSHOP','FOOD','OTHER')),
    depot_id            UUID         REFERENCES fuel_depots (id),
    status              VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    created_by          UUID,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version             BIGINT       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_fuel_ts_offering_service ON fuel_truck_stop_offerings (tenant_id, bookings_service_id);

CREATE TABLE fuel_truck_stop_reservations (
    id                    UUID         PRIMARY KEY,
    tenant_id             UUID         NOT NULL,
    offering_id           UUID         NOT NULL REFERENCES fuel_truck_stop_offerings (id),
    booking_id            UUID         NOT NULL,
    vehicle_registration  VARCHAR(20)  NOT NULL,
    driver_name           VARCHAR(120),
    driver_phone          VARCHAR(40),
    created_by            UUID,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version               BIGINT       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_fuel_ts_reservation_booking ON fuel_truck_stop_reservations (tenant_id, booking_id);
CREATE INDEX ix_fuel_ts_reservation_offering ON fuel_truck_stop_reservations (tenant_id, offering_id, created_at DESC);
CREATE INDEX ix_fuel_ts_reservation_vehicle ON fuel_truck_stop_reservations (tenant_id, upper(vehicle_registration));

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'FUEL_TRUCKSTOP_MANAGE', 'Set up truck stop services and take, cancel and complete truck stop reservations')
ON CONFLICT (name) DO NOTHING;
UPDATE permissions SET module_key = 'fuel', resource = 'truckstop', verb = 'MANAGE', sensitivity = 'NORMAL', requires_second_person = false, grantable_by = 'ADMIN'
 WHERE name = 'FUEL_TRUCKSTOP_MANAGE';
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
 WHERE r.name = 'ADMIN' AND p.name = 'FUEL_TRUCKSTOP_MANAGE'
   AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
