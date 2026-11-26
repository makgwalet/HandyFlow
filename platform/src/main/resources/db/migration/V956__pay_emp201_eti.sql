-- V956: SARS filing (phase 4). ETI facts on employees, the ETI claim flag on clients, and the monthly EMP201 figures.
ALTER TABLE pay_employees
    ADD COLUMN eti_id_kind          VARCHAR(10) CHECK (eti_id_kind IN ('SA_ID','ASYLUM','REFUGEE','OTHER')),
    ADD COLUMN domestic_worker      BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN connected_person     BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN below_minimum_wage   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN eti_months_prior     INTEGER NOT NULL DEFAULT 0 CHECK (eti_months_prior BETWEEN 0 AND 24);

ALTER TABLE pay_clients
    ADD COLUMN eti_claimable        BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN eti_claimable_note   VARCHAR(500),
    ADD COLUMN eti_claimable_set_by UUID,
    ADD COLUMN eti_claimable_set_at TIMESTAMPTZ;

-- One row per client per tax month. DRAFT may be re-prepared; FILED is fixed (the figures that were sent to SARS).
CREATE TABLE pay_emp201_returns (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    pay_client_id       UUID NOT NULL REFERENCES pay_clients(id),
    tax_month           DATE NOT NULL,               -- first day of the month
    status              VARCHAR(8) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','FILED')),
    employee_count      INTEGER NOT NULL,
    total_remuneration  NUMERIC(15,2) NOT NULL,
    paye                NUMERIC(15,2) NOT NULL,      -- PAYE withheld, before ETI
    uif_employee        NUMERIC(15,2) NOT NULL,
    uif_employer        NUMERIC(15,2) NOT NULL,
    sdl                 NUMERIC(15,2) NOT NULL,
    eti_calculated      NUMERIC(15,2) NOT NULL,      -- ETI worked out for the month
    eti_brought_forward NUMERIC(15,2) NOT NULL,      -- unused ETI from earlier months of the same tax year
    eti_used            NUMERIC(15,2) NOT NULL,      -- taken off the PAYE (zero when the client is not marked claimable)
    eti_carried_forward NUMERIC(15,2) NOT NULL,
    eti_claimed         BOOLEAN NOT NULL,            -- the client's claim flag at the time of preparing
    total_payable       NUMERIC(15,2) NOT NULL,      -- PAYE - ETI used + UIF + SDL
    due_date            DATE NOT NULL,
    run_numbers         VARCHAR(500),
    prepared_by         UUID,
    prepared_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    filed_by            UUID,
    filed_at            TIMESTAMPTZ,
    filing_reference    VARCHAR(100),
    version             BIGINT NOT NULL DEFAULT 0,
    UNIQUE (pay_client_id, tax_month)
);

CREATE TABLE pay_emp201_eti_lines (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    emp201_return_id    UUID NOT NULL REFERENCES pay_emp201_returns(id) ON DELETE CASCADE,
    pay_employee_id     UUID NOT NULL REFERENCES pay_employees(id),
    eligible            BOOLEAN NOT NULL,
    tier                VARCHAR(10),
    remuneration        NUMERIC(15,2) NOT NULL,
    incentive           NUMERIC(15,2) NOT NULL,
    reasons             VARCHAR(500),                -- why not eligible, as codes
    rule_version        VARCHAR(30) NOT NULL
);
CREATE INDEX ix_pay_emp201_eti_employee ON pay_emp201_eti_lines (pay_employee_id);
