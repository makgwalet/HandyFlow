-- Training Provider 0177: internal quality audits.
-- The provider writes its own checklists (versioned, immutable once saved; no regulatory content is seeded) and runs audits against a site,
-- a cohort, a programme, or in general. Each audit snapshots the checklist items it was run against. An item is answered "no issue seen", "issue",
-- "note" or "not applicable" by the auditor, always their own judgement. Issues lead to corrective actions with an owner and a due date. Completing an
-- audit and verifying an action each need a second person, or a written reason. Nothing is deleted: cancelled or retired with a reason.

CREATE TABLE trainprov_audit_templates (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    template_no    VARCHAR(30) NOT NULL,
    name           VARCHAR(200) NOT NULL,
    scope_type     VARCHAR(20) NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    retired_reason TEXT,
    created_by     UUID NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    version        BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_trainprov_audit_template_no UNIQUE (tenant_id, template_no),
    CONSTRAINT ck_trainprov_audit_template_name CHECK (COALESCE(char_length(btrim(name)), 0) >= 2),
    CONSTRAINT ck_trainprov_audit_template_scope CHECK (COALESCE(scope_type IN ('GENERAL', 'SITE', 'COHORT', 'PROGRAMME'), FALSE)),
    CONSTRAINT ck_trainprov_audit_template_status CHECK (COALESCE(status IN ('ACTIVE', 'RETIRED'), FALSE)),
    CONSTRAINT ck_trainprov_audit_template_retired CHECK (
        (status = 'ACTIVE' AND retired_reason IS NULL) OR (status = 'RETIRED' AND COALESCE(char_length(btrim(retired_reason)), 0) >= 10))
);

CREATE TABLE trainprov_audit_template_versions (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    template_id UUID NOT NULL REFERENCES trainprov_audit_templates(id),
    version_no  INTEGER NOT NULL,
    items_text  TEXT NOT NULL,
    change_note TEXT,
    created_by  UUID NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_trainprov_audit_template_version UNIQUE (template_id, version_no),
    CONSTRAINT ck_trainprov_audit_template_version_no CHECK (version_no >= 1),
    CONSTRAINT ck_trainprov_audit_template_items CHECK (COALESCE(char_length(btrim(items_text)), 0) >= 2)
);

CREATE TABLE trainprov_audits (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    audit_no             VARCHAR(30) NOT NULL,
    template_version_id  UUID NOT NULL REFERENCES trainprov_audit_template_versions(id),
    title                VARCHAR(200) NOT NULL,
    scope_type           VARCHAR(20) NOT NULL,
    site_id              UUID REFERENCES trainprov_delivery_sites(id),
    cohort_id            UUID REFERENCES trainprov_cohorts(id),
    programme_id         UUID REFERENCES trainprov_programmes(id),
    lead_user_id         UUID NOT NULL,
    planned_on           DATE NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    started_on           DATE,
    completed_on         DATE,
    completed_by         UUID,
    completed_alone_reason TEXT,
    summary              TEXT,
    cancelled_reason     TEXT,
    created_by           UUID NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    version              BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_trainprov_audit_no UNIQUE (tenant_id, audit_no),
    CONSTRAINT ck_trainprov_audit_title CHECK (COALESCE(char_length(btrim(title)), 0) >= 2),
    CONSTRAINT ck_trainprov_audit_scope CHECK (COALESCE(
        (scope_type = 'GENERAL' AND site_id IS NULL AND cohort_id IS NULL AND programme_id IS NULL)
        OR (scope_type = 'SITE' AND site_id IS NOT NULL AND cohort_id IS NULL AND programme_id IS NULL)
        OR (scope_type = 'COHORT' AND cohort_id IS NOT NULL AND site_id IS NULL AND programme_id IS NULL)
        OR (scope_type = 'PROGRAMME' AND programme_id IS NOT NULL AND site_id IS NULL AND cohort_id IS NULL), FALSE)),
    CONSTRAINT ck_trainprov_audit_status CHECK (COALESCE(status IN ('PLANNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'), FALSE)),
    CONSTRAINT ck_trainprov_audit_state CHECK (COALESCE(
        (status = 'PLANNED' AND started_on IS NULL AND completed_on IS NULL AND cancelled_reason IS NULL)
        OR (status = 'IN_PROGRESS' AND started_on IS NOT NULL AND completed_on IS NULL AND cancelled_reason IS NULL)
        OR (status = 'COMPLETED' AND started_on IS NOT NULL AND completed_on IS NOT NULL AND completed_by IS NOT NULL
            AND COALESCE(char_length(btrim(summary)), 0) >= 10 AND cancelled_reason IS NULL)
        OR (status = 'CANCELLED' AND completed_on IS NULL AND COALESCE(char_length(btrim(cancelled_reason)), 0) >= 10), FALSE)),
    CONSTRAINT ck_trainprov_audit_alone CHECK (completed_alone_reason IS NULL OR COALESCE(char_length(btrim(completed_alone_reason)), 0) >= 10)
);
CREATE INDEX idx_trainprov_audit_status ON trainprov_audits (tenant_id, status, planned_on);

CREATE TABLE trainprov_audit_items (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    audit_id    UUID NOT NULL REFERENCES trainprov_audits(id),
    position    INTEGER NOT NULL,
    question    TEXT NOT NULL,
    outcome     VARCHAR(20),
    note        TEXT,
    answered_by UUID,
    answered_at TIMESTAMPTZ,
    version     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_trainprov_audit_item UNIQUE (audit_id, position),
    CONSTRAINT ck_trainprov_audit_item_question CHECK (COALESCE(char_length(btrim(question)), 0) >= 2),
    CONSTRAINT ck_trainprov_audit_item_outcome CHECK (outcome IS NULL OR COALESCE(outcome IN ('NO_ISSUE', 'ISSUE', 'NOTE', 'NOT_APPLICABLE'), FALSE)),
    CONSTRAINT ck_trainprov_audit_item_answered CHECK (
        (outcome IS NULL AND answered_by IS NULL AND answered_at IS NULL)
        OR (outcome IS NOT NULL AND answered_by IS NOT NULL AND answered_at IS NOT NULL
            AND (outcome = 'NO_ISSUE' OR COALESCE(char_length(btrim(note)), 0) >= 10)))
);

CREATE TABLE trainprov_audit_actions (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    audit_id              UUID NOT NULL REFERENCES trainprov_audits(id),
    item_id               UUID REFERENCES trainprov_audit_items(id),
    description           TEXT NOT NULL,
    owner_name            VARCHAR(200) NOT NULL,
    due_on                DATE NOT NULL,
    status                VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    done_on               DATE,
    done_note             TEXT,
    done_by               UUID,
    verified_by           UUID,
    verified_at           TIMESTAMPTZ,
    verified_alone_reason TEXT,
    cancelled_reason      TEXT,
    created_by            UUID NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_trainprov_audit_action_desc CHECK (COALESCE(char_length(btrim(description)), 0) >= 5),
    CONSTRAINT ck_trainprov_audit_action_owner CHECK (COALESCE(char_length(btrim(owner_name)), 0) >= 2),
    CONSTRAINT ck_trainprov_audit_action_status CHECK (COALESCE(status IN ('OPEN', 'DONE', 'CANCELLED'), FALSE)),
    CONSTRAINT ck_trainprov_audit_action_state CHECK (COALESCE(
        (status = 'OPEN' AND done_on IS NULL AND done_by IS NULL AND verified_by IS NULL AND cancelled_reason IS NULL)
        OR (status = 'DONE' AND done_on IS NOT NULL AND done_by IS NOT NULL AND COALESCE(char_length(btrim(done_note)), 0) >= 10 AND cancelled_reason IS NULL
            AND ((verified_by IS NULL AND verified_at IS NULL) OR (verified_by IS NOT NULL AND verified_at IS NOT NULL)))
        OR (status = 'CANCELLED' AND COALESCE(char_length(btrim(cancelled_reason)), 0) >= 10 AND verified_by IS NULL), FALSE)),
    CONSTRAINT ck_trainprov_audit_action_alone CHECK (verified_alone_reason IS NULL OR COALESCE(char_length(btrim(verified_alone_reason)), 0) >= 10)
);
CREATE INDEX idx_trainprov_audit_action_audit ON trainprov_audit_actions (tenant_id, audit_id, status);
CREATE INDEX idx_trainprov_audit_action_due ON trainprov_audit_actions (tenant_id, status, due_on);
