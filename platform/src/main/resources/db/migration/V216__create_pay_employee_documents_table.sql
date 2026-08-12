CREATE TABLE pay_employee_documents (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL,
    pay_employee_id   UUID NOT NULL REFERENCES pay_employees(id),
    doc_type          VARCHAR(30) NOT NULL,
    file_name         VARCHAR(255) NOT NULL,
    content_type      VARCHAR(100),
    storage_key       VARCHAR(500) NOT NULL,
    file_size_bytes   BIGINT,
    uploaded_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_pay_employee_documents_employee ON pay_employee_documents (pay_employee_id);