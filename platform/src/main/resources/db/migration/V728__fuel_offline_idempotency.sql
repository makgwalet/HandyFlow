-- Offline Forecourt and Truck stop writes: the phone makes one id per shift open/close and per stay book/check-in/check-out
-- and sends it again after a lost reply or a reconnect. The same V719 pattern: unique per tenant, partial on not-null, so every
-- existing row and every online client is untouched. Additive only.

ALTER TABLE fuel_shifts ADD COLUMN open_request_id UUID;
ALTER TABLE fuel_shifts ADD COLUMN close_request_id UUID;
-- When the phone says it was captured. Informational: the system times (opened_at, closed_at) stay the server's.
ALTER TABLE fuel_shifts ADD COLUMN captured_opened_at TIMESTAMPTZ;
ALTER TABLE fuel_shifts ADD COLUMN captured_closed_at TIMESTAMPTZ;
CREATE UNIQUE INDEX uq_fuel_shift_open_request ON fuel_shifts (tenant_id, open_request_id) WHERE open_request_id IS NOT NULL;
CREATE UNIQUE INDEX uq_fuel_shift_close_request ON fuel_shifts (tenant_id, close_request_id) WHERE close_request_id IS NOT NULL;

ALTER TABLE fuel_truck_stop_stays ADD COLUMN book_request_id UUID;
ALTER TABLE fuel_truck_stop_stays ADD COLUMN check_in_request_id UUID;
ALTER TABLE fuel_truck_stop_stays ADD COLUMN check_out_request_id UUID;
ALTER TABLE fuel_truck_stop_stays ADD COLUMN captured_check_in_at TIMESTAMPTZ;
ALTER TABLE fuel_truck_stop_stays ADD COLUMN captured_check_out_at TIMESTAMPTZ;
CREATE UNIQUE INDEX uq_fuel_stay_book_request ON fuel_truck_stop_stays (tenant_id, book_request_id) WHERE book_request_id IS NOT NULL;
CREATE UNIQUE INDEX uq_fuel_stay_checkin_request ON fuel_truck_stop_stays (tenant_id, check_in_request_id) WHERE check_in_request_id IS NOT NULL;
CREATE UNIQUE INDEX uq_fuel_stay_checkout_request ON fuel_truck_stop_stays (tenant_id, check_out_request_id) WHERE check_out_request_id IS NOT NULL;

-- A queued write the server refused when it finally arrived (the meter had moved on, the bay was taken, the stay was already
-- checked in by someone else). Kept so a supervisor can see what was lost and decide; nothing here changes stock or money.
CREATE TABLE fuel_offline_conflicts (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    kind VARCHAR(30) NOT NULL,
    client_request_id UUID NOT NULL,
    entity_id UUID,
    user_id UUID,
    reason VARCHAR(500) NOT NULL,
    captured_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    status VARCHAR(10) NOT NULL DEFAULT 'OPEN',
    resolved_by UUID,
    resolved_at TIMESTAMPTZ,
    resolution_note VARCHAR(300)
);
CREATE UNIQUE INDEX uq_fuel_offline_conflict ON fuel_offline_conflicts (tenant_id, kind, client_request_id);
CREATE INDEX idx_fuel_offline_conflict_open ON fuel_offline_conflicts (tenant_id, status, created_at DESC);
