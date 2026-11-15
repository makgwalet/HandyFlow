-- Eligibility, step 3: product compatibility at tanker compartment level. A compartment with no rating restricts nothing, and no changeover rule means no
-- cleaning is required, so existing companies see no change until they set these up. Every rule here is the company's own.
CREATE TABLE fuel_compartment_products (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    compartment_id  UUID NOT NULL,
    fuel_type       VARCHAR(30) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_fuel_compartment_products ON fuel_compartment_products (tenant_id, compartment_id, fuel_type);

-- Carrying to_fuel in a compartment that last carried from_fuel needs a cleaning recorded after that cargo.
CREATE TABLE fuel_changeover_rules (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    from_fuel   VARCHAR(30) NOT NULL,
    to_fuel     VARCHAR(30) NOT NULL,
    note        VARCHAR(300),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_fuel_changeover_differs CHECK (from_fuel <> to_fuel)
);
CREATE UNIQUE INDEX uq_fuel_changeover_rules ON fuel_changeover_rules (tenant_id, from_fuel, to_fuel);

-- Cleaning and decontamination of one compartment, with its certificate or evidence. Append only.
CREATE TABLE fuel_compartment_cleanings (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    compartment_id   UUID NOT NULL,
    cleaned_at       TIMESTAMPTZ NOT NULL,
    method           VARCHAR(120),
    certificate_ref  VARCHAR(120),
    evidence_note    VARCHAR(300),
    recorded_by      UUID,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_fuel_cleaning_evidence CHECK (certificate_ref IS NOT NULL OR evidence_note IS NOT NULL)
);
CREATE INDEX ix_fuel_compartment_cleanings ON fuel_compartment_cleanings (tenant_id, compartment_id, cleaned_at DESC);

-- When on, a compartment with no product rating is a Review on every allocation. Off by default.
ALTER TABLE fuel_settings ADD COLUMN require_compartment_rating BOOLEAN NOT NULL DEFAULT false;
