-- Records retention (Secure Document Access Engine, phase 3): retention policies, legal hold, and controlled two-step disposal.
--
-- One retention item per business record that holds files (a Projects document revision, a Document Service document). The owning
-- module starts the clock when the record reaches the end of its active life (a project is closed, a document is archived); the item
-- is due when the policy's years have passed. Nothing is ever deleted automatically: a disposal is PROPOSED by one person and
-- APPROVED by a different person, and only then are the files deleted. The business record and the retention item stay as the
-- tombstone (reference, hash, dates, who proposed and who approved).
--
-- Periods: no row in document_retention_policies means 7 years (the approved default). The actual periods per category and
-- jurisdiction must be set by the compliance owner; a policy row they have confirmed carries confirmed = true.

-- ── Permissions ─────────────────────────────────────────────────────────────
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'RECORDS_READ',    'See retention dates, legal holds, policies and disposal requests'),
    (gen_random_uuid(), 'RECORDS_MANAGE',  'Place and lift legal holds, set retention policies and propose disposals'),
    (gen_random_uuid(), 'RECORDS_APPROVE', 'Approve or reject a disposal proposed by someone else')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name IN ('ADMIN', 'OWNER') AND p.name IN ('RECORDS_READ', 'RECORDS_MANAGE', 'RECORDS_APPROVE')
ON CONFLICT DO NOTHING;

-- ── Policies ────────────────────────────────────────────────────────────────
-- category '*' applies to every category of that module that has no row of its own.
CREATE TABLE IF NOT EXISTS document_retention_policies (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    module_code      VARCHAR(40) NOT NULL,
    category         VARCHAR(60) NOT NULL,
    jurisdiction     VARCHAR(10) NOT NULL DEFAULT 'ZA',
    retain_years     INTEGER NOT NULL CHECK (retain_years BETWEEN 1 AND 100),
    personal_info    BOOLEAN NOT NULL DEFAULT FALSE,
    confirmed        BOOLEAN NOT NULL DEFAULT FALSE,
    notes            VARCHAR(500),
    updated_by       UUID,
    updated_by_name  VARCHAR(200),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_retention_policy UNIQUE (tenant_id, module_code, category, jurisdiction)
);

-- ── Retention items ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS document_retention_items (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    module_code      VARCHAR(40) NOT NULL,
    source_type      VARCHAR(40) NOT NULL,
    source_id        UUID NOT NULL,
    category         VARCHAR(60) NOT NULL,
    jurisdiction     VARCHAR(10) NOT NULL DEFAULT 'ZA',
    reference        VARCHAR(150),
    title            VARCHAR(300) NOT NULL,
    context_label    VARCHAR(200),
    clock_start      DATE,
    retain_until     DATE,
    policy_years     INTEGER,
    legal_hold       BOOLEAN NOT NULL DEFAULT FALSE,
    hold_reason      VARCHAR(500),
    hold_by          UUID,
    hold_by_name     VARCHAR(200),
    hold_at          TIMESTAMPTZ,
    status           VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    batch_id         UUID,
    disposed_at      TIMESTAMPTZ,
    disposal_detail  VARCHAR(1000),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_retention_item UNIQUE (tenant_id, module_code, source_type, source_id),
    CONSTRAINT chk_retention_item_status CHECK (status IN ('ACTIVE', 'PROPOSED', 'DISPOSED')),
    CONSTRAINT chk_retention_item_clock CHECK ((clock_start IS NULL) = (retain_until IS NULL) AND (clock_start IS NULL) = (policy_years IS NULL)),
    CONSTRAINT chk_retention_item_hold CHECK (NOT legal_hold OR hold_reason IS NOT NULL),
    CONSTRAINT chk_retention_item_proposed CHECK (status <> 'PROPOSED' OR batch_id IS NOT NULL),
    CONSTRAINT chk_retention_item_disposed CHECK ((status = 'DISPOSED') = (disposed_at IS NOT NULL)),
    -- A held item can sit in a proposal (it is skipped when the proposal is approved) but is never disposed.
    CONSTRAINT chk_retention_item_held_not_disposed CHECK (NOT (legal_hold AND status = 'DISPOSED'))
);
CREATE INDEX IF NOT EXISTS idx_retention_items_due ON document_retention_items (tenant_id, retain_until)
    WHERE status = 'ACTIVE' AND legal_hold = FALSE AND retain_until IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_retention_items_held ON document_retention_items (tenant_id) WHERE legal_hold;
CREATE INDEX IF NOT EXISTS idx_retention_items_batch ON document_retention_items (batch_id) WHERE batch_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_retention_items_policy ON document_retention_items (tenant_id, module_code, category, jurisdiction)
    WHERE status <> 'DISPOSED';

CREATE OR REPLACE FUNCTION document_retention_items_guard() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'A retention item is never deleted: it is the record of what was kept and what was disposed.';
    END IF;
    IF NEW.tenant_id <> OLD.tenant_id OR NEW.module_code <> OLD.module_code OR NEW.source_type <> OLD.source_type
       OR NEW.source_id <> OLD.source_id OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'What a retention item refers to cannot change.';
    END IF;
    IF OLD.status = 'DISPOSED' AND (ROW(NEW.*) IS DISTINCT FROM ROW(OLD.*)) THEN
        RAISE EXCEPTION 'A disposed record is final and cannot be changed.';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_document_retention_items_guard ON document_retention_items;
CREATE TRIGGER trg_document_retention_items_guard BEFORE UPDATE OR DELETE ON document_retention_items
    FOR EACH ROW EXECUTE FUNCTION document_retention_items_guard();

-- ── Disposal batches (two-step, two people) ─────────────────────────────────
CREATE TABLE IF NOT EXISTS document_disposal_batches (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    status            VARCHAR(10) NOT NULL DEFAULT 'PROPOSED',
    reason            VARCHAR(500) NOT NULL,
    proposed_by       UUID NOT NULL,
    proposed_by_name  VARCHAR(200),
    proposed_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_by        UUID,
    decided_by_name   VARCHAR(200),
    decided_at        TIMESTAMPTZ,
    decision_note     VARCHAR(500),
    cancelled_at      TIMESTAMPTZ,
    completed_at      TIMESTAMPTZ,
    item_count        INTEGER NOT NULL DEFAULT 0,
    disposed_count    INTEGER NOT NULL DEFAULT 0,
    skipped_count     INTEGER NOT NULL DEFAULT 0,
    failed_count      INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT chk_disposal_status CHECK (status IN ('PROPOSED', 'APPROVED', 'REJECTED', 'CANCELLED', 'COMPLETED')),
    -- The two-person rule, held by the database as well as the application.
    CONSTRAINT chk_disposal_two_people CHECK (decided_by IS NULL OR decided_by <> proposed_by),
    CONSTRAINT chk_disposal_decided CHECK ((status IN ('APPROVED', 'REJECTED', 'COMPLETED')) = (decided_by IS NOT NULL AND decided_at IS NOT NULL)),
    CONSTRAINT chk_disposal_rejected_note CHECK (status <> 'REJECTED' OR decision_note IS NOT NULL),
    CONSTRAINT chk_disposal_cancelled CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL)),
    CONSTRAINT chk_disposal_completed CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL))
);
CREATE INDEX IF NOT EXISTS idx_disposal_batches_tenant ON document_disposal_batches (tenant_id, proposed_at DESC);

CREATE OR REPLACE FUNCTION document_disposal_batches_guard() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'A disposal request is never deleted.';
    END IF;
    IF NEW.tenant_id <> OLD.tenant_id OR NEW.proposed_by <> OLD.proposed_by OR NEW.proposed_at <> OLD.proposed_at OR NEW.reason <> OLD.reason THEN
        RAISE EXCEPTION 'Who proposed a disposal, when and why cannot change.';
    END IF;
    IF OLD.decided_by IS NOT NULL AND (NEW.decided_by IS DISTINCT FROM OLD.decided_by OR NEW.decided_at IS DISTINCT FROM OLD.decided_at
                                       OR NEW.decision_note IS DISTINCT FROM OLD.decision_note) THEN
        RAISE EXCEPTION 'A disposal decision cannot be changed once made.';
    END IF;
    IF OLD.status IN ('REJECTED', 'CANCELLED', 'COMPLETED') AND (ROW(NEW.*) IS DISTINCT FROM ROW(OLD.*)) THEN
        RAISE EXCEPTION 'A finished disposal request cannot be changed.';
    END IF;
    IF NOT ((OLD.status = NEW.status)
            OR (OLD.status = 'PROPOSED' AND NEW.status IN ('APPROVED', 'REJECTED', 'CANCELLED'))
            OR (OLD.status = 'APPROVED' AND NEW.status = 'COMPLETED')) THEN
        RAISE EXCEPTION 'A disposal request cannot go from % to %.', OLD.status, NEW.status;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_document_disposal_batches_guard ON document_disposal_batches;
CREATE TRIGGER trg_document_disposal_batches_guard BEFORE UPDATE OR DELETE ON document_disposal_batches
    FOR EACH ROW EXECUTE FUNCTION document_disposal_batches_guard();

CREATE TABLE IF NOT EXISTS document_disposal_batch_items (
    batch_id   UUID NOT NULL REFERENCES document_disposal_batches(id),
    item_id    UUID NOT NULL REFERENCES document_retention_items(id),
    outcome    VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    detail     VARCHAR(500),
    done_at    TIMESTAMPTZ,
    PRIMARY KEY (batch_id, item_id),
    CONSTRAINT chk_disposal_item_outcome CHECK (outcome IN ('PENDING', 'DISPOSED', 'SKIPPED', 'FAILED'))
);
CREATE INDEX IF NOT EXISTS idx_disposal_batch_items_item ON document_disposal_batch_items (item_id);

-- ── Tombstone columns on the business records ───────────────────────────────
-- The record stays; its file is gone. Downloads of a disposed record are refused with 410.
ALTER TABLE project_documents
    ADD COLUMN IF NOT EXISTS disposed_at        TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS disposal_batch_id  UUID;
ALTER TABLE documents
    ADD COLUMN IF NOT EXISTS disposed_at        TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS disposal_batch_id  UUID;

-- ── Backfill: records that already exist ────────────────────────────────────
-- Projects: every register entry with a stored file. The clock starts on the day the project was closed (as V576 already stamped).
INSERT INTO document_retention_items (id, tenant_id, module_code, source_type, source_id, category, reference, title, context_label,
                                      clock_start, retain_until, policy_years, legal_hold, hold_reason, hold_at)
SELECT gen_random_uuid(), d.tenant_id, 'PROJECTS', 'DOCUMENT', d.id, d.document_type,
       left(coalesce(d.document_number, '') || coalesce(' rev ' || d.revision, ''), 150), left(d.title, 300), left(p.name, 200),
       c.closed_on, (c.closed_on + INTERVAL '7 years')::date, CASE WHEN c.closed_on IS NULL THEN NULL ELSE 7 END,
       d.legal_hold, CASE WHEN d.legal_hold THEN 'Carried over from the project document register' END,
       CASE WHEN d.legal_hold THEN now() END
FROM project_documents d
JOIN projects p ON p.id = d.project_id
CROSS JOIN LATERAL (SELECT CASE WHEN p.status = 'CLOSED' AND p.closed_at IS NOT NULL
                                THEN (p.closed_at AT TIME ZONE 'Africa/Johannesburg')::date END AS closed_on) c
WHERE d.evidence_id IS NOT NULL
ON CONFLICT (tenant_id, module_code, source_type, source_id) DO NOTHING;

-- Document Service: every document. The clock starts on the day it was archived.
INSERT INTO document_retention_items (id, tenant_id, module_code, source_type, source_id, category, reference, title,
                                      clock_start, retain_until, policy_years)
SELECT gen_random_uuid(), d.tenant_id, 'DOCUMENTS', 'DOCUMENT', d.id, d.category, left(d.owner_type, 150), left(d.title, 300),
       a.archived_on, (a.archived_on + INTERVAL '7 years')::date, CASE WHEN a.archived_on IS NULL THEN NULL ELSE 7 END
FROM documents d
CROSS JOIN LATERAL (SELECT CASE WHEN d.status = 'ARCHIVED' THEN coalesce(d.archived_at, d.updated_at)::date END AS archived_on) a
ON CONFLICT (tenant_id, module_code, source_type, source_id) DO NOTHING;
