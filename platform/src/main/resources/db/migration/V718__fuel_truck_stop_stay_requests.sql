-- A driver or fleet contact with portal access to a customer account asks for an overnight stay at the truck stop.
-- It is a request, not a booking: staff approve (which books through the normal stay code and charges the account) or decline.
CREATE TABLE fuel_truck_stop_stay_requests (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    account_id           UUID NOT NULL,
    portal_user_id       UUID NOT NULL,
    requested_by_email   VARCHAR(255),
    vehicle_registration VARCHAR(20) NOT NULL,
    driver_name          VARCHAR(120),
    driver_phone         VARCHAR(40),
    arrival_date         DATE NOT NULL,
    departure_date       DATE NOT NULL,
    note                 VARCHAR(300),
    status               VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','DECLINED','CANCELLED')),
    decided_by           UUID,
    decided_at           TIMESTAMPTZ,
    decision_note        VARCHAR(300),
    stay_id              UUID,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_fuel_stay_requests_dates CHECK (departure_date > arrival_date)
);
CREATE INDEX ix_fuel_stay_requests_account ON fuel_truck_stop_stay_requests (tenant_id, account_id, created_at DESC);
CREATE INDEX ix_fuel_stay_requests_status ON fuel_truck_stop_stay_requests (tenant_id, status, created_at DESC);
