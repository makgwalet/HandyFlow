-- V462: fields an account defines for its own customers, and the values stored on each customer.
CREATE TABLE crm_custom_fields (
    id          UUID PRIMARY KEY,
    tenant_id   UUID         NOT NULL,
    field_key   VARCHAR(40)  NOT NULL,
    label       VARCHAR(60)  NOT NULL,
    field_type  VARCHAR(20)  NOT NULL,
    required    BOOLEAN      NOT NULL DEFAULT FALSE,
    options     JSONB,
    sort_order  INT          NOT NULL DEFAULT 0,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_crm_custom_fields_key UNIQUE (tenant_id, field_key),
    CONSTRAINT chk_crm_custom_fields_type CHECK (field_type IN ('TEXT', 'NUMBER', 'DATE', 'CHOICE', 'YES_NO'))
);

ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS custom_values JSONB;
