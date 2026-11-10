-- Warehousing: cycle counts with variance approval.
--
-- A count session freezes what the system believes is on hand for a set of positions, someone counts, and every
-- difference must be approved by a different person (permission WAREHOUSING_COUNT_APPROVE) before it posts to the ledger as
-- a COUNT_VARIANCE movement. If the position moved while the count was open, the line goes STALE and has to be counted again.

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'WAREHOUSING_COUNT_APPROVE', 'Approve or reject a cycle-count variance counted by someone else')
ON CONFLICT (name) DO NOTHING;

UPDATE permissions
   SET module_key = 'warehousing', resource = 'cycle_count', verb = 'APPROVE', sensitivity = 'SENSITIVE',
       requires_second_person = true, grantable_by = 'MODULE_ADMIN'
 WHERE name = 'WAREHOUSING_COUNT_APPROVE';

INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.id
  FROM role_permissions rp
  JOIN permissions ap ON ap.id = rp.permission_id AND ap.name = 'WAREHOUSING_ADMIN'
  CROSS JOIN permissions np
 WHERE np.name = 'WAREHOUSING_COUNT_APPROVE'
ON CONFLICT DO NOTHING;

CREATE TABLE whse_count_sessions (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    count_number  VARCHAR(30) NOT NULL,
    client_id     UUID REFERENCES whse_clients (id),
    location_id   UUID REFERENCES whse_locations (id),
    blind         BOOLEAN NOT NULL DEFAULT TRUE,
    status        VARCHAR(12) NOT NULL DEFAULT 'COUNTING' CHECK (status IN ('COUNTING','IN_REVIEW','CLOSED','CANCELLED')),
    note          VARCHAR(300),
    created_by    UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    submitted_at  TIMESTAMPTZ,
    closed_at     TIMESTAMPTZ,
    closed_by     UUID,
    version       BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_whse_count_number UNIQUE (tenant_id, count_number)
);
CREATE INDEX idx_whse_count_sessions_open ON whse_count_sessions (tenant_id, created_at DESC);

CREATE TABLE whse_count_lines (
    id                  UUID PRIMARY KEY,
    session_id          UUID NOT NULL REFERENCES whse_count_sessions (id),
    tenant_id           UUID NOT NULL,
    inventory_id        UUID NOT NULL REFERENCES whse_inventory (id),
    client_id           UUID NOT NULL,
    item_id             UUID NOT NULL,
    location_id         UUID NOT NULL,
    stock_status        VARCHAR(20) NOT NULL,
    expected_qty        NUMERIC(12,3) NOT NULL,
    expected_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- the first count of the line, never changed: stock accuracy is measured on it, not on recounts
    initial_counted_qty NUMERIC(12,3) CHECK (initial_counted_qty IS NULL OR initial_counted_qty >= 0),
    counted_qty         NUMERIC(12,3) CHECK (counted_qty IS NULL OR counted_qty >= 0),
    counted_by          UUID,
    counted_at          TIMESTAMPTZ,
    state               VARCHAR(18) NOT NULL DEFAULT 'OPEN'
        CHECK (state IN ('OPEN','COUNTED','MATCHED','PENDING_APPROVAL','APPLIED','RECOUNT','STALE')),
    decided_by          UUID,
    decided_at          TIMESTAMPTZ,
    decision_note       VARCHAR(500),
    sole_exception      BOOLEAN NOT NULL DEFAULT FALSE,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_whse_count_line_position UNIQUE (session_id, inventory_id)
);
CREATE INDEX idx_whse_count_lines_session ON whse_count_lines (session_id);
CREATE INDEX idx_whse_count_lines_accuracy ON whse_count_lines (tenant_id, counted_at) WHERE initial_counted_qty IS NOT NULL;
