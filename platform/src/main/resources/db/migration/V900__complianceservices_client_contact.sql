-- Compliance Services: contact between a client's portal users and the practice. A client raises a query and the practice answers in the same
-- thread; the practice can ask for a document. Nothing is deleted. A query is closed by the practice, never by the client.

CREATE TABLE IF NOT EXISTS compliance_portal_queries (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL,
    client_id          UUID NOT NULL REFERENCES compliance_service_clients(id),
    subject            VARCHAR(200) NOT NULL,
    status             VARCHAR(10) NOT NULL DEFAULT 'OPEN',
    raised_by          UUID NOT NULL,
    raised_by_name     VARCHAR(255),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_message_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    message_count      INTEGER NOT NULL DEFAULT 0,
    closed_by          UUID,
    closed_at          TIMESTAMPTZ,
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_compliance_portal_query_status CHECK (status IN ('OPEN', 'ANSWERED', 'CLOSED')),
    CONSTRAINT ck_compliance_portal_query_closed CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL AND closed_by IS NOT NULL)),
    CONSTRAINT ck_compliance_portal_query_count CHECK (message_count >= 0)
);
CREATE INDEX IF NOT EXISTS idx_compliance_portal_query_client ON compliance_portal_queries(tenant_id, client_id);
CREATE INDEX IF NOT EXISTS idx_compliance_portal_query_status ON compliance_portal_queries(tenant_id, status);

CREATE TABLE IF NOT EXISTS compliance_portal_messages (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    query_id    UUID NOT NULL REFERENCES compliance_portal_queries(id),
    author_type VARCHAR(6) NOT NULL,
    author_id   UUID NOT NULL,
    author_name VARCHAR(255),
    body        TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_compliance_portal_message_author CHECK (author_type IN ('CLIENT', 'STAFF')),
    CONSTRAINT ck_compliance_portal_message_body CHECK (length(btrim(body)) BETWEEN 1 AND 4000)
);
CREATE INDEX IF NOT EXISTS idx_compliance_portal_message_query ON compliance_portal_messages(tenant_id, query_id, created_at);

CREATE TABLE IF NOT EXISTS compliance_document_requests (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    client_id     UUID NOT NULL REFERENCES compliance_service_clients(id),
    title         VARCHAR(200) NOT NULL,
    description   TEXT,
    document_type VARCHAR(100),
    due_date      DATE,
    status        VARCHAR(10) NOT NULL DEFAULT 'OPEN',
    requested_by  UUID,
    requested_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    document_id   UUID,
    closed_by     UUID,
    closed_at     TIMESTAMPTZ,
    close_note    TEXT,
    version       BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_compliance_document_request_status CHECK (status IN ('OPEN', 'RECEIVED', 'CLOSED', 'CANCELLED')),
    CONSTRAINT ck_compliance_document_request_done CHECK ((status IN ('CLOSED', 'CANCELLED')) = (closed_at IS NOT NULL AND closed_by IS NOT NULL AND length(btrim(coalesce(close_note, ''))) >= 10))
);
CREATE INDEX IF NOT EXISTS idx_compliance_document_request_client ON compliance_document_requests(tenant_id, client_id);
CREATE INDEX IF NOT EXISTS idx_compliance_document_request_status ON compliance_document_requests(tenant_id, status);
