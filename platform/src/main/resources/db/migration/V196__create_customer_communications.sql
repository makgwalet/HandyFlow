-- Rename this file to the actual next Flyway version before running.

CREATE TABLE customer_communications (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL,
    customer_id  UUID NOT NULL REFERENCES customers(id),
    type         VARCHAR(20) NOT NULL,
    direction    VARCHAR(10) NOT NULL,
    summary      TEXT NOT NULL,
    occurred_at  TIMESTAMPTZ NOT NULL,
    logged_by    UUID,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_customer_communications_customer ON customer_communications (tenant_id, customer_id, occurred_at DESC);