-- Training Provider 0174: booking site resources into timetable slots.
-- A booking says a slot uses a quantity of a resource from the register. A non-shareable resource cannot be booked beyond its quantity at
-- overlapping times unless an administrator approves it with a written reason, which is kept on the booking. Bookings are released with a
-- reason, never deleted. Booking says nothing about whether a resource satisfies any standard.

CREATE TABLE trainprov_slot_resources (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    slot_id         UUID NOT NULL REFERENCES trainprov_timetable_slots(id),
    resource_id     UUID NOT NULL REFERENCES trainprov_site_resources(id),
    quantity        INTEGER NOT NULL DEFAULT 1,
    status          VARCHAR(10) NOT NULL DEFAULT 'BOOKED',
    clash_note      TEXT,
    override_reason TEXT,
    override_by     UUID,
    override_at     TIMESTAMPTZ,
    released_reason TEXT,
    released_by     UUID,
    released_at     TIMESTAMPTZ,
    created_by      UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_trainprov_slot_res_qty CHECK (quantity >= 1 AND quantity <= 100000),
    CONSTRAINT ck_trainprov_slot_res_status CHECK (COALESCE(status IN ('BOOKED', 'RELEASED'), FALSE)),
    CONSTRAINT ck_trainprov_slot_res_override CHECK (
        (override_reason IS NULL AND override_by IS NULL AND override_at IS NULL AND clash_note IS NULL)
        OR (COALESCE(char_length(btrim(override_reason)), 0) >= 10 AND override_by IS NOT NULL AND override_at IS NOT NULL
            AND COALESCE(char_length(btrim(clash_note)), 0) >= 1)),
    CONSTRAINT ck_trainprov_slot_res_released CHECK (
        (status = 'BOOKED' AND released_reason IS NULL AND released_by IS NULL AND released_at IS NULL)
        OR (status = 'RELEASED' AND COALESCE(char_length(btrim(released_reason)), 0) >= 10 AND released_by IS NOT NULL AND released_at IS NOT NULL))
);
CREATE UNIQUE INDEX uq_trainprov_slot_res_live ON trainprov_slot_resources (slot_id, resource_id) WHERE status = 'BOOKED';
CREATE INDEX idx_trainprov_slot_res_resource ON trainprov_slot_resources (tenant_id, resource_id, status);
CREATE INDEX idx_trainprov_slot_res_slot ON trainprov_slot_resources (tenant_id, slot_id);
