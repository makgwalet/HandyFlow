-- Eligibility, step 2: a delivery site can require approved drivers. Nothing is required until a site's rule says so, so existing companies see no
-- change. A suspension recorded against a driver at a site blocks them whether or not the site requires approvals.
ALTER TABLE fuel_site_access_rules
    ADD COLUMN require_approved_drivers BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN required_training        VARCHAR(120);

CREATE TABLE fuel_site_driver_approvals (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    site_id           UUID NOT NULL,
    employee_id       UUID NOT NULL,
    status            VARCHAR(12) NOT NULL,
    induction_date    DATE,
    induction_expiry  DATE,
    training_ref      VARCHAR(120),
    training_expiry   DATE,
    conditions        VARCHAR(500),
    updated_by        UUID,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_fuel_site_driver_status CHECK (status IN ('APPROVED','RESTRICTED','SUSPENDED')),
    CONSTRAINT ck_fuel_site_driver_dates CHECK (induction_expiry IS NULL OR induction_date IS NULL OR induction_expiry >= induction_date)
);
CREATE UNIQUE INDEX uq_fuel_site_driver_approvals ON fuel_site_driver_approvals (tenant_id, site_id, employee_id);
