-- src/main/resources/db/migration/V45__create_tenant_number_sequences.sql

-- WHY this table instead of a Postgres SEQUENCE per tenant?
-- Native SEQUENCEs aren't tenant-scoped, and dynamically CREATE-ing one per
-- tenant at onboarding is far more operational surface area (migration
-- ordering, backups, permissions) than a single row-based table keyed by
-- (tenant_id, sequence_name). One row per counter is trivial to reason
-- about, inspect, and manually correct if a tenant ever needs it.
CREATE TABLE tenant_number_sequences (
    tenant_id      UUID        NOT NULL,
    sequence_name  VARCHAR(50) NOT NULL,
    last_value     BIGINT      NOT NULL DEFAULT 0,
    updated_at     TIMESTAMP   NOT NULL DEFAULT now(),

    CONSTRAINT pk_tenant_number_sequences PRIMARY KEY (tenant_id, sequence_name)
);

-- ── Backfill: seed each tenant's counter to the highest number already in use ──
--
-- WHY parse the numeric suffix instead of using COUNT(*)?
-- QuoteService.convertToInvoice previously generated invoice numbers like
-- "INV-A1B2C3D4" (first 8 chars of the quote UUID) instead of calling the
-- shared generator. Those malformed rows may already exist in production.
-- A COUNT(*)-based seed would produce a "next number" unrelated to the
-- highest real sequential number already issued, risking collision with an
-- existing INV-000042-style row. Filtering to well-formed numbers via regex
-- and taking MAX() of the parsed integer is the only safe way to resume
-- numbering without colliding or skipping.
INSERT INTO tenant_number_sequences (tenant_id, sequence_name, last_value)
SELECT
    tenant_id,
    'INVOICE',
    COALESCE(MAX(
        CASE WHEN invoice_number ~ '^INV-\d+$'
             THEN CAST(substring(invoice_number FROM 5) AS BIGINT)
             ELSE 0
        END
    ), 0)
FROM invoices
GROUP BY tenant_id
ON CONFLICT (tenant_id, sequence_name) DO NOTHING;

INSERT INTO tenant_number_sequences (tenant_id, sequence_name, last_value)
SELECT
    tenant_id,
    'QUOTE',
    COALESCE(MAX(
        CASE WHEN quote_number ~ '^QT-\d+$'
             THEN CAST(substring(quote_number FROM 4) AS BIGINT)
             ELSE 0
        END
    ), 0)
FROM quotes
GROUP BY tenant_id
ON CONFLICT (tenant_id, sequence_name) DO NOTHING;

-- Tenants with zero existing invoices/quotes get no row here — that's fine,
-- the INSERT ... ON CONFLICT in TenantSequenceService creates their row
-- lazily starting at 1 the first time they generate a number.