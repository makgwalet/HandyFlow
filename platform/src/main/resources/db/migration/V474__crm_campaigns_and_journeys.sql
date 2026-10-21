-- V474: campaigns (a named list of people with a goal) and journeys (the timed steps run for each person in an active campaign).
CREATE TABLE crm_campaigns (
    id         UUID PRIMARY KEY,
    tenant_id  UUID         NOT NULL,
    name       VARCHAR(120) NOT NULL,
    goal       VARCHAR(500),
    status     VARCHAR(12)  NOT NULL,                    -- DRAFT, ACTIVE, PAUSED, COMPLETED
    starts_on  DATE,
    ends_on    DATE,
    created_by UUID,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT chk_crm_campaign_dates CHECK (ends_on IS NULL OR starts_on IS NULL OR ends_on >= starts_on)
);
CREATE INDEX idx_crm_campaigns_tenant ON crm_campaigns (tenant_id) WHERE deleted_at IS NULL;

CREATE TABLE crm_campaign_members (
    id          UUID PRIMARY KEY,
    tenant_id   UUID        NOT NULL,
    campaign_id UUID        NOT NULL REFERENCES crm_campaigns (id),
    customer_id UUID        NOT NULL,
    status      VARCHAR(12) NOT NULL,                    -- ADDED, CONTACTED, RESPONDED, CONVERTED
    added_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    status_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    steps_done  INTEGER     NOT NULL DEFAULT 0,          -- how many journey steps have run for this person
    exited_at   TIMESTAMPTZ,                             -- the journey stopped for this person (they replied, converted, or the lead closed)
    CONSTRAINT uq_crm_campaign_member UNIQUE (campaign_id, customer_id)
);
CREATE INDEX idx_crm_campaign_members_campaign ON crm_campaign_members (campaign_id);
CREATE INDEX idx_crm_campaign_members_customer ON crm_campaign_members (tenant_id, customer_id);

CREATE TABLE crm_journey_steps (
    id          UUID PRIMARY KEY,
    tenant_id   UUID         NOT NULL,
    campaign_id UUID         NOT NULL REFERENCES crm_campaigns (id),
    position    INTEGER      NOT NULL,
    day_offset  INTEGER      NOT NULL CHECK (day_offset BETWEEN 0 AND 365),   -- days after the person was added
    action      VARCHAR(20)  NOT NULL,                   -- CREATE_TASK, ADD_TAG, ASSIGN_OWNER, NOTIFY_OWNER
    step_text   VARCHAR(500),
    action_user UUID,
    CONSTRAINT uq_crm_journey_step UNIQUE (campaign_id, position)
);

CREATE TABLE crm_journey_runs (
    id            UUID PRIMARY KEY,
    tenant_id     UUID         NOT NULL,
    campaign_id   UUID         NOT NULL REFERENCES crm_campaigns (id),
    customer_id   UUID         NOT NULL,
    step_position INTEGER      NOT NULL,
    outcome       VARCHAR(10)  NOT NULL,                 -- DONE or FAILED
    detail        VARCHAR(500),
    ran_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_crm_journey_runs_campaign ON crm_journey_runs (campaign_id, ran_at DESC);
