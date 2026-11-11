-- Warehousing usage ledger: one row for every goods receipt and customer order successfully created, written in the same transaction as the record itself.
-- Append-only: a row is never changed or removed, so a count for any billing month can be explained later. A cancelled receipt or order stays counted; editing,
-- receiving against or re-processing one does not add a row (the unique key is the source record). The monthly limit counts these rows, not live table rows.
CREATE TABLE IF NOT EXISTS whse_usage_events (
    id          UUID PRIMARY KEY,
    tenant_id   UUID        NOT NULL,
    meter       VARCHAR(30) NOT NULL CHECK (meter IN ('TRANSACTION')),
    source_type VARCHAR(20) NOT NULL CHECK (source_type IN ('RECEIPT','ORDER')),
    source_id   UUID        NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_whse_usage_source UNIQUE (tenant_id, meter, source_type, source_id)
);
CREATE INDEX IF NOT EXISTS idx_whse_usage_tenant_time ON whse_usage_events (tenant_id, meter, occurred_at);

CREATE OR REPLACE FUNCTION whse_usage_events_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'whse_usage_events is append-only';
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_whse_usage_events_immutable ON whse_usage_events;
CREATE TRIGGER trg_whse_usage_events_immutable BEFORE UPDATE OR DELETE ON whse_usage_events FOR EACH ROW EXECUTE FUNCTION whse_usage_events_immutable();

-- History: what already exists counts from the day it was created, so the first month is not understated.
INSERT INTO whse_usage_events (id, tenant_id, meter, source_type, source_id, occurred_at)
SELECT gen_random_uuid(), tenant_id, 'TRANSACTION', 'RECEIPT', id, created_at FROM whse_inbound_shipments
ON CONFLICT (tenant_id, meter, source_type, source_id) DO NOTHING;
INSERT INTO whse_usage_events (id, tenant_id, meter, source_type, source_id, occurred_at)
SELECT gen_random_uuid(), tenant_id, 'TRANSACTION', 'ORDER', id, created_at FROM whse_outbound_orders
ON CONFLICT (tenant_id, meter, source_type, source_id) DO NOTHING;
