-- Phase 7c (B06b): supply contracts that GROUP and REPORT, nothing more. A contract ties a customer account to dates, an optional
-- volume commitment and terms, and links to the account's existing price entries (B07). It holds NO prices of its own: the price
-- list stays the only price master, so approvals and margin checks on prices still apply.
CREATE TABLE fuel_contracts (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    account_id       UUID NOT NULL REFERENCES fuel_customer_accounts(id),
    contract_number  VARCHAR(40) NOT NULL,
    title            VARCHAR(200) NOT NULL,
    start_date       DATE NOT NULL,
    end_date         DATE NOT NULL,
    fuel_type        VARCHAR(30),
    committed_litres NUMERIC(14,2),
    terms            VARCHAR(2000),
    status           VARCHAR(12) NOT NULL DEFAULT 'ACTIVE',
    cancelled_reason VARCHAR(300),
    cancelled_at     TIMESTAMPTZ,
    created_by       UUID,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at       TIMESTAMPTZ,
    version          BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_fuel_contracts_dates CHECK (end_date >= start_date),
    CONSTRAINT ck_fuel_contracts_committed CHECK (committed_litres IS NULL OR committed_litres > 0),
    CONSTRAINT ck_fuel_contracts_status CHECK (status IN ('ACTIVE','CANCELLED')),
    CONSTRAINT ck_fuel_contracts_cancel CHECK (status <> 'CANCELLED' OR (cancelled_reason IS NOT NULL AND cancelled_at IS NOT NULL))
);
CREATE UNIQUE INDEX uq_fuel_contracts_number ON fuel_contracts (tenant_id, lower(contract_number)) WHERE deleted_at IS NULL;
CREATE INDEX idx_fuel_contracts_account ON fuel_contracts (tenant_id, account_id) WHERE deleted_at IS NULL;

-- Which existing price entries a contract refers to. A link changes nothing about the price.
CREATE TABLE fuel_contract_prices (
    contract_id UUID NOT NULL REFERENCES fuel_contracts(id),
    price_id    UUID NOT NULL REFERENCES fuel_prices(id),
    tenant_id   UUID NOT NULL,
    linked_by   UUID,
    linked_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (contract_id, price_id)
);
CREATE INDEX idx_fuel_contract_prices_price ON fuel_contract_prices (tenant_id, price_id);
