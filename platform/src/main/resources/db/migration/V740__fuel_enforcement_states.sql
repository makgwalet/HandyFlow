-- Enforcement rollout: each control (ARRIVAL_CHECK, POD_POLICY) is OFF, MONITOR, PILOT or ENFORCED per company, with a pilot scope of depots.
-- Companies that already had a switch on keep it: it becomes ENFORCED here, so nothing changes for them.
CREATE TABLE IF NOT EXISTS fuel_enforcement (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    control         VARCHAR(30) NOT NULL,
    state           VARCHAR(12) NOT NULL DEFAULT 'OFF',
    pilot_depot_ids TEXT,
    reason          VARCHAR(500),
    state_since     TIMESTAMPTZ NOT NULL DEFAULT now(),
    changed_by      UUID,
    version_no      INT NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_fuel_enforcement ON fuel_enforcement (tenant_id, control);

-- What a control would have refused while it was only watching (MONITOR, or PILOT outside its scope). Never blocks anything.
CREATE TABLE IF NOT EXISTS fuel_enforcement_observations (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    control     VARCHAR(30) NOT NULL,
    delivery_id UUID,
    message     VARCHAR(500) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_fuel_enforcement_obs ON fuel_enforcement_observations (tenant_id, control, created_at);

INSERT INTO fuel_enforcement (id, tenant_id, control, state, reason)
SELECT gen_random_uuid(), tenant_id, 'ARRIVAL_CHECK', 'ENFORCED', 'Carried over from the existing switch' FROM fuel_settings WHERE arrival_check_enforced
ON CONFLICT DO NOTHING;
INSERT INTO fuel_enforcement (id, tenant_id, control, state, reason)
SELECT gen_random_uuid(), tenant_id, 'POD_POLICY', 'ENFORCED', 'Carried over from the existing switch' FROM fuel_settings WHERE pod_policy_enforced
ON CONFLICT DO NOTHING;
