-- V___fleet_drivers.sql
-- (rename to the next available Flyway version number in your sequence)

CREATE TABLE fleet_drivers (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id        UUID         NOT NULL,
    first_name       VARCHAR(100) NOT NULL,
    last_name        VARCHAR(100) NOT NULL,
    phone            VARCHAR(50),
    email            VARCHAR(255),
    id_number        VARCHAR(50),   -- SA ID or passport number

    -- Standard driving license
    license_number   VARCHAR(50),
    license_code     VARCHAR(10),   -- A, A1, B, C1, C, EB, EC1, EC
    license_expiry   DATE,

    -- Professional Driving Permit (required for public transport, goods
    -- vehicles over a GVM threshold, or dangerous goods — not every driver)
    prdp_required    BOOLEAN      NOT NULL DEFAULT FALSE,
    prdp_number      VARCHAR(50),
    prdp_category    VARCHAR(5),    -- G (goods), P (passengers), D (dangerous goods)
    prdp_expiry      DATE,

    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    notes            TEXT,
    created_at       TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT now(),
    deleted_at       TIMESTAMP,
    deleted_by       UUID,
    version          BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_fleet_drivers PRIMARY KEY (id),
    CONSTRAINT chk_driver_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_driver_license_code CHECK (
        license_code IS NULL OR license_code IN ('A','A1','B','C1','C','EB','EC1','EC')
    ),
    CONSTRAINT chk_driver_prdp_category CHECK (
        prdp_category IS NULL OR prdp_category IN ('G','P','D')
    )
);

CREATE INDEX idx_fleet_drivers_tenant ON fleet_drivers (tenant_id) WHERE deleted_at IS NULL;

CREATE INDEX idx_fleet_drivers_license_expiry ON fleet_drivers (license_expiry)
    WHERE deleted_at IS NULL AND status = 'ACTIVE' AND license_expiry IS NOT NULL;
CREATE INDEX idx_fleet_drivers_prdp_expiry ON fleet_drivers (prdp_expiry)
    WHERE deleted_at IS NULL AND status = 'ACTIVE' AND prdp_required = TRUE AND prdp_expiry IS NOT NULL;

-- The real link from Vehicle to Driver. assigned_driver_name (existing
-- column) is left as-is — a free-text fallback, not replaced — so no
-- existing data is lost or needs migrating; this is purely additive.
ALTER TABLE fleet_vehicles
    ADD COLUMN IF NOT EXISTS assigned_driver_id UUID REFERENCES fleet_drivers(id);

CREATE INDEX IF NOT EXISTS idx_fleet_vehicles_driver ON fleet_vehicles (assigned_driver_id)
    WHERE assigned_driver_id IS NOT NULL;
