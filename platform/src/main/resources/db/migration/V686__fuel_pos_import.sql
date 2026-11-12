-- Phase 8e (D03): inbound pump/POS shift import. Per-tenant API keys (only the hash is kept) and a review queue.
-- An import is only staged here. Nothing reaches shifts or stock until a supervisor accepts it, and accepting runs the
-- normal open-shift and close-shift code. New tables only.

CREATE TABLE fuel_import_keys (
    id            UUID PRIMARY KEY,
    tenant_id     UUID         NOT NULL,
    name          VARCHAR(150) NOT NULL,
    key_hash      VARCHAR(64)  NOT NULL UNIQUE,
    key_prefix    VARCHAR(16)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by    UUID,
    last_used_at  TIMESTAMPTZ,
    revoked_at    TIMESTAMPTZ
);
CREATE INDEX ix_fuel_import_keys_tenant ON fuel_import_keys (tenant_id);

CREATE TABLE fuel_pos_imports (
    id            UUID PRIMARY KEY,
    tenant_id     UUID         NOT NULL,
    key_id        UUID         REFERENCES fuel_import_keys (id),
    source        VARCHAR(40)  NOT NULL,
    external_id   VARCHAR(100) NOT NULL,
    payload       TEXT         NOT NULL,
    payload_hash  VARCHAR(64)  NOT NULL,
    problems      TEXT,
    status        VARCHAR(10)  NOT NULL DEFAULT 'PENDING',
    received_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    decided_by    UUID,
    decided_at    TIMESTAMPTZ,
    decision_note VARCHAR(500),
    shift_id      UUID         REFERENCES fuel_shifts (id),
    CONSTRAINT ck_fuel_pos_imports_status CHECK (status IN ('PENDING','ACCEPTED','REJECTED')),
    CONSTRAINT uq_fuel_pos_imports_ref UNIQUE (tenant_id, source, external_id)
);
CREATE INDEX ix_fuel_pos_imports_queue ON fuel_pos_imports (tenant_id, status, received_at DESC);
