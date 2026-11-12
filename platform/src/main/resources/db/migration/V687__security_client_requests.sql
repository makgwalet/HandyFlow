-- Guard app offline writes (assessment G3). The phone gives each scan, incident report and
-- panic press a client-generated id before it is sent. If the signal drops and the phone
-- sends it again, the server finds the id here and returns the first result instead of
-- creating a second record. One row per accepted request.
CREATE TABLE security_client_requests (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL REFERENCES tenants(id),
    guard_id    UUID         NOT NULL,
    kind        VARCHAR(20)  NOT NULL CHECK (kind IN ('SCAN', 'INCIDENT', 'DURESS')),
    client_ref  UUID         NOT NULL,
    result_id   UUID         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_security_client_requests UNIQUE (tenant_id, kind, client_ref)
);

CREATE INDEX ix_security_client_requests_created ON security_client_requests (created_at);
