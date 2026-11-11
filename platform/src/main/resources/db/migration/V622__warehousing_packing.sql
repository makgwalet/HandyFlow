-- V622: Packing. An outbound order can be packed into numbered packages (box, pallet, ...), each with its contents, dimensions and weight.
--
-- Packing is optional for an order: with no packages the order can be marked packed as before. Once an order has a package, every unit that
-- will ship must be in a closed package before the order can be marked packed. A closed package has a gross weight and a printable label.

CREATE TABLE whse_packages (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    order_id         UUID NOT NULL REFERENCES whse_outbound_orders (id),
    package_no       INTEGER NOT NULL CHECK (package_no > 0),
    package_type     VARCHAR(20) NOT NULL DEFAULT 'BOX' CHECK (package_type IN ('BOX','PALLET','ENVELOPE','CRATE','OTHER')),
    length_mm        INTEGER CHECK (length_mm > 0),
    width_mm         INTEGER CHECK (width_mm > 0),
    height_mm        INTEGER CHECK (height_mm > 0),
    tare_kg          NUMERIC(8,3) NOT NULL DEFAULT 0 CHECK (tare_kg >= 0),
    gross_kg         NUMERIC(8,3) CHECK (gross_kg > 0),
    status           VARCHAR(10) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','CLOSED')),
    created_by       UUID,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at        TIMESTAMPTZ,
    label_printed_at TIMESTAMPTZ,
    CONSTRAINT uq_whse_package_no UNIQUE (order_id, package_no),
    CONSTRAINT chk_whse_package_closed CHECK (status = 'OPEN' OR (gross_kg IS NOT NULL AND closed_at IS NOT NULL)),
    CONSTRAINT chk_whse_package_gross CHECK (gross_kg IS NULL OR gross_kg > tare_kg)
);
CREATE INDEX idx_whse_packages_order ON whse_packages (tenant_id, order_id);

CREATE TABLE whse_package_items (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    package_id    UUID NOT NULL REFERENCES whse_packages (id) ON DELETE CASCADE,
    order_line_id UUID NOT NULL REFERENCES whse_outbound_order_lines (id),
    qty           NUMERIC(12,3) NOT NULL CHECK (qty > 0),
    CONSTRAINT uq_whse_package_item UNIQUE (package_id, order_line_id)
);
CREATE INDEX idx_whse_package_items_line ON whse_package_items (order_line_id);
