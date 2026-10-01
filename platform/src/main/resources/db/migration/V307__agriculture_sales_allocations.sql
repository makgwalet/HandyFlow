-- V307__agriculture_sales_allocations.sql
--
-- ADR-001, W2: Agriculture does NOT own sales. An invoice line stays in Invoicing; this table only records which part of it belongs to
-- which crop cycle, group, animal or enterprise, so revenue can be attributed to production. Revenue itself is NOT stored: it is computed
-- live from the invoice (ex-VAT, net of credit notes, only while the invoice is issued and not cancelled), so a cancelled invoice or a later
-- credit note is reflected instead of going stale.
--
-- invoice_number and description are copied for display only (so a list still reads sensibly if the invoice is later unreachable).
-- Allocations are removed, never deleted: status REMOVED keeps who and when.
-- For livestock the allocation is also the SALE EVENT: Agriculture has no other record of when, how many, or for how much animals were sold
-- (an animal's status becomes SOLD with no date or price). Recording it does NOT change the herd: it does not mark animals sold or reduce a count.
CREATE TABLE ag_sales_allocations (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    farm_id          UUID NOT NULL,
    invoice_id       UUID NOT NULL,
    invoice_line_id  UUID NOT NULL,
    invoice_number   VARCHAR(50),
    description      VARCHAR(255),
    target_type      VARCHAR(20) NOT NULL,
    target_id        UUID NOT NULL,
    quantity         NUMERIC(14,3) NOT NULL,
    unit             VARCHAR(20),
    head_count       INTEGER,
    sold_on          DATE NOT NULL,
    notes            TEXT,
    status           VARCHAR(10) NOT NULL,
    removed_at       TIMESTAMPTZ,
    removed_by       UUID,
    created_by       UUID,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    version          BIGINT,
    CONSTRAINT chk_ag_sales_alloc_target   CHECK (target_type IN ('CROP_CYCLE', 'GROUP', 'ANIMAL', 'ENTERPRISE')),
    CONSTRAINT chk_ag_sales_alloc_status   CHECK (status IN ('ACTIVE', 'REMOVED')),
    CONSTRAINT chk_ag_sales_alloc_quantity CHECK (quantity > 0),
    CONSTRAINT chk_ag_sales_alloc_heads    CHECK (head_count IS NULL OR head_count > 0)
);
CREATE INDEX idx_ag_sales_alloc_farm   ON ag_sales_allocations (tenant_id, farm_id, sold_on);
CREATE INDEX idx_ag_sales_alloc_target ON ag_sales_allocations (tenant_id, target_type, target_id);
CREATE INDEX idx_ag_sales_alloc_line   ON ag_sales_allocations (tenant_id, invoice_line_id);
