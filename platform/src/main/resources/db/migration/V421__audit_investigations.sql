-- A20 (part 2): investigation case files. A case is visible only to the people named on it. The permission AUDIT_INVESTIGATE is created here
-- but given to nobody, not even the ADMIN role: a company decides who may open cases, and membership of the case is a second, separate lock.
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'AUDIT_INVESTIGATE', 'Open and work on investigation case files (also requires being named on the case)')
ON CONFLICT (name) DO NOTHING;

CREATE TABLE audit_investigations (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    number        VARCHAR(12) NOT NULL,
    title         VARCHAR(255) NOT NULL,
    summary       TEXT,
    status        VARCHAR(12) NOT NULL DEFAULT 'OPEN',      -- OPEN | UNDER_REVIEW | CLOSED
    opened_by     UUID NOT NULL,
    opened_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_by     UUID,
    closed_at     TIMESTAMPTZ,
    close_reason  TEXT,
    CONSTRAINT uq_audit_investigation_number UNIQUE (tenant_id, number),
    CONSTRAINT ck_audit_investigation_status CHECK (status IN ('OPEN','UNDER_REVIEW','CLOSED'))
);

CREATE TABLE audit_investigation_members (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    investigation_id UUID NOT NULL REFERENCES audit_investigations(id) ON DELETE CASCADE,
    user_id          UUID NOT NULL,
    user_name        VARCHAR(255) NOT NULL,
    role             VARCHAR(12) NOT NULL,                  -- LEAD | INVESTIGATOR | OBSERVER
    added_by         UUID NOT NULL,
    added_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    removed_at       TIMESTAMPTZ,
    CONSTRAINT ck_audit_investigation_role CHECK (role IN ('LEAD','INVESTIGATOR','OBSERVER'))
);
CREATE UNIQUE INDEX uq_audit_investigation_member_live ON audit_investigation_members (investigation_id, user_id) WHERE removed_at IS NULL;
CREATE INDEX idx_audit_investigation_member_user ON audit_investigation_members (tenant_id, user_id) WHERE removed_at IS NULL;

-- Append-only, hash-chained record of everything that happens on the case (notes, evidence, transfers, member changes, views, closure).
CREATE TABLE audit_investigation_log (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    investigation_id UUID NOT NULL REFERENCES audit_investigations(id) ON DELETE CASCADE,
    seq              INT NOT NULL,
    at               TIMESTAMPTZ NOT NULL,
    actor_id         UUID NOT NULL,
    actor_name       VARCHAR(255) NOT NULL,
    action           VARCHAR(30) NOT NULL,
    detail           TEXT,
    prev_hash        VARCHAR(64) NOT NULL,
    hash             VARCHAR(64) NOT NULL,
    CONSTRAINT uq_audit_investigation_log_seq UNIQUE (investigation_id, seq)
);

CREATE TABLE audit_investigation_evidence (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    investigation_id UUID NOT NULL REFERENCES audit_investigations(id) ON DELETE CASCADE,
    ref              VARCHAR(10) NOT NULL,
    description      TEXT NOT NULL,
    source           VARCHAR(255),
    collected_by     UUID NOT NULL,
    collected_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    holder_id        UUID NOT NULL,
    holder_name      VARCHAR(255) NOT NULL,
    CONSTRAINT uq_audit_investigation_evidence_ref UNIQUE (investigation_id, ref)
);
