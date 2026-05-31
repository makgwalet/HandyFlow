ALTER TABLE security_incidents
    ALTER COLUMN type SET DEFAULT 'GENERAL';

ALTER TABLE security_sites
    ADD COLUMN IF NOT EXISTS contract_status    VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS contract_start     DATE,
    ADD COLUMN IF NOT EXISTS contract_end       DATE,
    ADD COLUMN IF NOT EXISTS termination_reason TEXT,
    ADD COLUMN IF NOT EXISTS terminated_at      TIMESTAMPTZ;