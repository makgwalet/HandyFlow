-- Supply Chain: supplier compliance documents. Files live in the shared evidence store; evidence_id points at it.

CREATE TABLE IF NOT EXISTS sc_supplier_documents (
    id               UUID PRIMARY KEY,
    tenant_id        UUID         NOT NULL,
    supplier_id      UUID         NOT NULL REFERENCES sc_suppliers(id),
    doc_type         VARCHAR(40)  NOT NULL,
    reference_number VARCHAR(100),
    issued_date      DATE,
    expiry_date      DATE,
    status           VARCHAR(12)  NOT NULL DEFAULT 'ACTIVE',
    verified         BOOLEAN      NOT NULL DEFAULT FALSE,
    verified_by      UUID,
    verified_by_name VARCHAR(255),
    verified_at      TIMESTAMPTZ,
    evidence_id      UUID,
    file_name        VARCHAR(255),
    content_type     VARCHAR(100),
    uploaded_by      UUID,
    uploaded_by_name VARCHAR(255),
    removed_reason   VARCHAR(500),
    removed_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_sc_supplier_doc_status CHECK (status IN ('ACTIVE','SUPERSEDED','REMOVED')),
    CONSTRAINT chk_sc_supplier_doc_dates CHECK (issued_date IS NULL OR expiry_date IS NULL OR expiry_date >= issued_date)
);
CREATE INDEX IF NOT EXISTS idx_sc_supplier_docs_supplier ON sc_supplier_documents(tenant_id, supplier_id, status);
CREATE INDEX IF NOT EXISTS idx_sc_supplier_docs_expiry ON sc_supplier_documents(expiry_date) WHERE status = 'ACTIVE';
