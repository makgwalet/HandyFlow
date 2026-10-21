-- V469: CRM workflow automation. "When X happens (and the lead fits), do Y", with a log of every time one ran.
CREATE TABLE crm_workflows (
    id             UUID PRIMARY KEY,
    tenant_id      UUID         NOT NULL,
    name           VARCHAR(120) NOT NULL,
    trigger_event  VARCHAR(30)  NOT NULL,
    trigger_stage  VARCHAR(20),                       -- only for STAGE_CHANGED: the stage moved to (null = any)
    cond_field     VARCHAR(20),                       -- optional extra condition on the lead
    cond_op        VARCHAR(10),
    cond_value     VARCHAR(100),
    action         VARCHAR(30)  NOT NULL,
    action_text    VARCHAR(500),                      -- task note, tag or message
    action_days    INTEGER,                           -- CREATE_TASK: due this many days after the event
    action_user    UUID,                              -- ASSIGN_OWNER: the new owner; CREATE_TASK: who does it (null = the lead's owner)
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at     TIMESTAMPTZ,
    CONSTRAINT chk_crm_workflows_cond CHECK ((cond_field IS NULL AND cond_op IS NULL AND cond_value IS NULL) OR (cond_field IS NOT NULL AND cond_op IS NOT NULL AND cond_value IS NOT NULL))
);
CREATE INDEX idx_crm_workflows_tenant ON crm_workflows (tenant_id, trigger_event) WHERE deleted_at IS NULL;

CREATE TABLE crm_workflow_runs (
    id           UUID PRIMARY KEY,
    tenant_id    UUID         NOT NULL,
    workflow_id  UUID         NOT NULL REFERENCES crm_workflows (id),
    customer_id  UUID         NOT NULL,
    outcome      VARCHAR(10)  NOT NULL CHECK (outcome IN ('DONE', 'FAILED')),
    detail       VARCHAR(500),
    ran_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_crm_workflow_runs_tenant ON crm_workflow_runs (tenant_id, ran_at DESC);
