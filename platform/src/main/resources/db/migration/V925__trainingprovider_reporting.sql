-- Training provider: regulatory reporting. Nothing here is hard-coded. A report obligation is set up by the provider for an authority (and optionally one
-- programme): its cadence, the number of days after a period ends that it falls due, and a written source note saying where that rule came from.
-- A period is one reporting window under an obligation. A submission is recorded as "submitted by staff". The authority's acknowledgement is recorded
-- separately. Whether a period is overdue is worked out from its due day, never stored. Nothing here says a provider is compliant.

CREATE TABLE trainprov_report_obligations (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    obligation_no       VARCHAR(30) NOT NULL,
    authority_id        UUID NOT NULL REFERENCES trainprov_authorities(id),
    programme_id        UUID REFERENCES trainprov_programmes(id),
    name                VARCHAR(200) NOT NULL,
    cadence             VARCHAR(20) NOT NULL CHECK (cadence IN ('MONTHLY', 'QUARTERLY', 'HALF_YEARLY', 'ANNUAL', 'ONE_OFF')),
    due_days_after_end  INTEGER NOT NULL CHECK (due_days_after_end BETWEEN 0 AND 366),
    source_note         TEXT NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'RETIRED')),
    retired_reason      TEXT,
    retired_by          UUID,
    retired_at          TIMESTAMPTZ,
    created_by          UUID NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_trainprov_obligation_no UNIQUE (tenant_id, obligation_no),
    CONSTRAINT ck_trainprov_obligation_name CHECK (COALESCE(char_length(btrim(name)), 0) >= 2),
    CONSTRAINT ck_trainprov_obligation_source CHECK (COALESCE(char_length(btrim(source_note)), 0) >= 10),
    CONSTRAINT ck_trainprov_obligation_status CHECK (
        (status = 'ACTIVE' AND retired_reason IS NULL AND retired_by IS NULL AND retired_at IS NULL)
        OR (status = 'RETIRED' AND COALESCE(char_length(btrim(retired_reason)), 0) >= 10 AND retired_by IS NOT NULL AND retired_at IS NOT NULL))
);
CREATE UNIQUE INDEX uq_trainprov_obligation_live ON trainprov_report_obligations
    (tenant_id, authority_id, COALESCE(programme_id, '00000000-0000-0000-0000-000000000000'::uuid), lower(name)) WHERE status = 'ACTIVE';

CREATE TABLE trainprov_report_periods (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    period_no             VARCHAR(30) NOT NULL,
    obligation_id         UUID NOT NULL REFERENCES trainprov_report_obligations(id),
    period_start          DATE NOT NULL,
    period_end            DATE NOT NULL,
    due_on                DATE NOT NULL,
    due_override_reason   TEXT,
    status                VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'SUBMITTED', 'WAIVED', 'VOID')),
    submitted_on          DATE,
    submitted_by          UUID,
    submission_ref        VARCHAR(200),
    acknowledged_on       DATE,
    acknowledgement_ref   VARCHAR(200),
    waived_reason         TEXT,
    waived_by             UUID,
    void_reason           TEXT,
    void_by               UUID,
    note                  TEXT,
    created_by            UUID NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_trainprov_period_no UNIQUE (tenant_id, period_no),
    CONSTRAINT ck_trainprov_period_dates CHECK (period_end >= period_start),
    CONSTRAINT ck_trainprov_period_override CHECK (due_override_reason IS NULL OR COALESCE(char_length(btrim(due_override_reason)), 0) >= 10),
    CONSTRAINT ck_trainprov_period_status CHECK (
        (status = 'OPEN' AND submitted_on IS NULL AND submitted_by IS NULL AND acknowledged_on IS NULL AND waived_reason IS NULL AND void_reason IS NULL)
        OR (status = 'SUBMITTED' AND submitted_on IS NOT NULL AND submitted_by IS NOT NULL AND waived_reason IS NULL AND void_reason IS NULL)
        OR (status = 'WAIVED' AND COALESCE(char_length(btrim(waived_reason)), 0) >= 10 AND waived_by IS NOT NULL AND submitted_on IS NULL AND acknowledged_on IS NULL AND void_reason IS NULL)
        OR (status = 'VOID' AND COALESCE(char_length(btrim(void_reason)), 0) >= 10 AND void_by IS NOT NULL)),
    CONSTRAINT ck_trainprov_period_ack CHECK (acknowledged_on IS NOT NULL OR acknowledgement_ref IS NULL),
    CONSTRAINT ck_trainprov_period_ack_after CHECK (acknowledged_on IS NULL OR submitted_on IS NULL OR acknowledged_on >= submitted_on)
);
-- One live period per window per obligation. A void one stays on record and the window can be created again.
CREATE UNIQUE INDEX uq_trainprov_period_live ON trainprov_report_periods (tenant_id, obligation_id, period_start, period_end) WHERE status <> 'VOID';
CREATE INDEX idx_trainprov_period_due ON trainprov_report_periods (tenant_id, status, due_on);
