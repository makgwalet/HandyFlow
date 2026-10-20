-- Earthmoving: provider-neutral telematics. No provider is chosen; this holds the mapping of machines to devices and the normalised readings
-- any provider adapter (or a person) supplies. Provider readings and manual readings are kept apart and never overwrite the machine's own meter.
CREATE TABLE earthmoving_telematics_devices (
    id            UUID PRIMARY KEY,
    tenant_id     UUID         NOT NULL,
    asset_id      UUID         NOT NULL REFERENCES earthmoving_assets (id),
    provider_code VARCHAR(40)  NOT NULL,
    external_id   VARCHAR(120) NOT NULL,
    label         VARCHAR(120),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    mapped_by     UUID         NOT NULL,
    mapped_at     TIMESTAMPTZ  NOT NULL,
    unmapped_by   UUID,
    unmapped_at   TIMESTAMPTZ,
    unmap_reason  TEXT,
    CONSTRAINT chk_em_tdev_unmap CHECK (active OR (unmapped_by IS NOT NULL AND unmapped_at IS NOT NULL AND unmap_reason IS NOT NULL))
);
-- A device belongs to one machine at a time, and a machine has one active device per provider.
CREATE UNIQUE INDEX uq_em_tdev_device ON earthmoving_telematics_devices (tenant_id, provider_code, external_id) WHERE active;
CREATE UNIQUE INDEX uq_em_tdev_asset  ON earthmoving_telematics_devices (tenant_id, asset_id, provider_code) WHERE active;

CREATE TABLE earthmoving_telematics_readings (
    id             UUID PRIMARY KEY,
    tenant_id      UUID         NOT NULL,
    asset_id       UUID         NOT NULL REFERENCES earthmoving_assets (id),
    device_id      UUID REFERENCES earthmoving_telematics_devices (id),
    source         VARCHAR(10)  NOT NULL,
    provider_code  VARCHAR(40),
    recorded_at    TIMESTAMPTZ  NOT NULL,
    received_at    TIMESTAMPTZ  NOT NULL,
    latitude       NUMERIC(9, 6),
    longitude      NUMERIC(9, 6),
    meter_value    NUMERIC(12, 2),
    meter_unit     VARCHAR(10),
    entered_by     UUID,
    note           TEXT,
    CONSTRAINT chk_em_tread_source CHECK (source IN ('PROVIDER', 'MANUAL')),
    CONSTRAINT chk_em_tread_lat    CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT chk_em_tread_lon    CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    CONSTRAINT chk_em_tread_pair   CHECK ((latitude IS NULL) = (longitude IS NULL)),
    CONSTRAINT chk_em_tread_meter  CHECK (meter_value IS NULL OR (meter_value >= 0 AND meter_unit IN ('HOURS', 'KILOMETRES'))),
    CONSTRAINT chk_em_tread_some   CHECK (latitude IS NOT NULL OR meter_value IS NOT NULL),
    CONSTRAINT chk_em_tread_prov   CHECK (source <> 'PROVIDER' OR (device_id IS NOT NULL AND provider_code IS NOT NULL)),
    CONSTRAINT chk_em_tread_man    CHECK (source <> 'MANUAL' OR entered_by IS NOT NULL)
);
-- The same provider reading delivered twice is stored once.
CREATE UNIQUE INDEX uq_em_tread_dup ON earthmoving_telematics_readings (device_id, recorded_at) WHERE device_id IS NOT NULL;
CREATE INDEX idx_em_tread_latest ON earthmoving_telematics_readings (tenant_id, asset_id, source, recorded_at DESC);
