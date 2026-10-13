-- RENAME to the next free Flyway version, e.g. V123__debtcollection_work_items.sql
-- ASSUMPTION: AggregateRoot maps id, tenant_id, created_at, updated_at, version.
-- Compare with an existing debtcollection_* migration and align base columns before applying.

CREATE TABLE debtcollection_work_items (
    id                       UUID PRIMARY KEY,
    tenant_id                UUID         NOT NULL,
    created_at               TIMESTAMPTZ  NOT NULL,
    updated_at               TIMESTAMPTZ,
    version                  BIGINT       NOT NULL DEFAULT 0,
    case_id                  UUID         NOT NULL REFERENCES debtcollection_cases (id),
    type                     VARCHAR(30)  NOT NULL,
    status                   VARCHAR(20)  NOT NULL,
    title                    VARCHAR(500) NOT NULL,
    due_date                 DATE,
    priority_score           INT          NOT NULL DEFAULT 0,
    priority_reasons         TEXT,
    priority_override        INT,
    priority_override_reason VARCHAR(500),
    assigned_to_user_id      UUID,
    assigned_to_user_name    VARCHAR(255),
    snoozed_until            DATE,
    snooze_reason            VARCHAR(500),
    disposition              VARCHAR(30),
    disposition_notes        TEXT,
    completed_at             TIMESTAMPTZ,
    completed_by             UUID,
    source_key               VARCHAR(120),
    created_by               UUID
);

-- Idempotent generation: one item per trigger per tenant, regardless of status.
CREATE UNIQUE INDEX uq_dc_work_items_source ON debtcollection_work_items (tenant_id, source_key)
    WHERE source_key IS NOT NULL;
-- Queue read path.
CREATE INDEX ix_dc_work_items_queue ON debtcollection_work_items (tenant_id, assigned_to_user_id, status);
CREATE INDEX ix_dc_work_items_case ON debtcollection_work_items (case_id);

CREATE TABLE debtcollection_work_item_events (
    id               UUID PRIMARY KEY,
    tenant_id        UUID         NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ,
    version          BIGINT       NOT NULL DEFAULT 0,
    work_item_id     UUID         NOT NULL REFERENCES debtcollection_work_items (id),
    case_id          UUID         NOT NULL,
    event_type       VARCHAR(40)  NOT NULL,
    detail           TEXT,
    actor_user_id    UUID,
    actor_user_name  VARCHAR(255)
);
CREATE INDEX ix_dc_work_item_events_item ON debtcollection_work_item_events (work_item_id, created_at);
