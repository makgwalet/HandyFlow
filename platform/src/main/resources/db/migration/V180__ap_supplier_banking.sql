-- V__PLACEHOLDER_ap_supplier_banking.sql
-- RENAME: replace __PLACEHOLDER__ with your real next Flyway version.
--
-- Keyed on (tenant_id, LOWER(supplier_name)), not a supplier_id — because
-- ap_bills.supplier_id is essentially never populated across this whole
-- system (every bill checked this session has it null). Building a full
-- Supplier entity with real foreign keys would need bill creation to
-- change too, which is out of scope here. This matches the exact
-- established pattern ApPdfGenerator.generateSupplierStatement() already
-- uses — matching suppliers by name, explicitly documented there as
-- intentional for the same reason.

CREATE TABLE ap_supplier_banking (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    supplier_name   VARCHAR(255) NOT NULL,
    bank_name       VARCHAR(255),
    account_holder  VARCHAR(255),
    account_number  VARCHAR(50) NOT NULL,
    branch_code     VARCHAR(20) NOT NULL,
    vat_number      VARCHAR(30),
    notes           TEXT,
    created_by      UUID,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL
);

-- One banking record per supplier name per tenant — case-insensitive, so
-- "Sasol Oil" and "sasol oil" can't both get separate rows.
CREATE UNIQUE INDEX idx_ap_supplier_banking_tenant_name
    ON ap_supplier_banking(tenant_id, LOWER(supplier_name));