-- Training Provider 0170: moderation sampling.
-- A sampling rule is the provider's own way of choosing which decided attempts get moderated. It is not a regulatory requirement and
-- nothing here says it is. A rule has versions; a version never changes after it is saved. A draw records the rule and version it used,
-- a readable copy of the settings, the seed, the whole eligible population and which records were picked and why. Nothing is deleted.
-- The moderation record itself (V920) is unchanged: findings stay there. No percentages or thresholds are seeded.

CREATE TABLE trainprov_sampling_rules (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    rule_no          VARCHAR(30) NOT NULL,
    name             VARCHAR(200) NOT NULL,
    scope_type       VARCHAR(20) NOT NULL CHECK (scope_type IN ('ALL', 'PROGRAMME', 'ASSESSMENT_TYPE', 'COHORT')),
    programme_id     UUID REFERENCES trainprov_programmes(id),
    cohort_id        UUID REFERENCES trainprov_cohorts(id),
    assessment_type  VARCHAR(20),
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'RETIRED')),
    retired_reason   TEXT,
    retired_by       UUID,
    retired_at       TIMESTAMPTZ,
    created_by       UUID NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    version          BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_trainprov_samprule_no UNIQUE (tenant_id, rule_no),
    CONSTRAINT ck_trainprov_samprule_name CHECK (COALESCE(char_length(btrim(name)), 0) >= 2),
    CONSTRAINT ck_trainprov_samprule_scope CHECK (
        (scope_type = 'ALL' AND programme_id IS NULL AND cohort_id IS NULL AND assessment_type IS NULL)
        OR (scope_type = 'PROGRAMME' AND programme_id IS NOT NULL AND cohort_id IS NULL AND assessment_type IS NULL)
        OR (scope_type = 'COHORT' AND cohort_id IS NOT NULL AND programme_id IS NULL AND assessment_type IS NULL)
        OR (scope_type = 'ASSESSMENT_TYPE' AND COALESCE(assessment_type IN ('KNOWLEDGE', 'PRACTICAL', 'WORKPLACE', 'INTEGRATED', 'PORTFOLIO'), FALSE)
            AND programme_id IS NULL AND cohort_id IS NULL)),
    CONSTRAINT ck_trainprov_samprule_retired CHECK (
        (status = 'ACTIVE' AND retired_reason IS NULL AND retired_by IS NULL AND retired_at IS NULL)
        OR (status = 'RETIRED' AND COALESCE(char_length(btrim(retired_reason)), 0) >= 10 AND retired_by IS NOT NULL AND retired_at IS NOT NULL))
);
-- One live rule per scope.
CREATE UNIQUE INDEX uq_trainprov_samprule_live ON trainprov_sampling_rules
    (tenant_id, scope_type, COALESCE(programme_id, '00000000-0000-0000-0000-000000000000'::uuid),
     COALESCE(cohort_id, '00000000-0000-0000-0000-000000000000'::uuid), COALESCE(assessment_type, ''))
    WHERE status = 'ACTIVE';

CREATE TABLE trainprov_sampling_rule_versions (
    id                     UUID PRIMARY KEY,
    tenant_id              UUID NOT NULL,
    rule_id                UUID NOT NULL REFERENCES trainprov_sampling_rules(id),
    version_no             INTEGER NOT NULL CHECK (version_no >= 1),
    effective_from         DATE NOT NULL,
    basis                  VARCHAR(20) NOT NULL CHECK (basis IN ('ALL', 'PERCENTAGE')),
    percentage             NUMERIC(5,2),
    min_count              INTEGER CHECK (min_count >= 0),
    max_count              INTEGER CHECK (max_count >= 1),
    selection_method       VARCHAR(20) NOT NULL CHECK (selection_method IN ('RANDOM', 'RISK_BASED')),
    risk_factors           VARCHAR(300),
    extra_trigger_queries  INTEGER,
    extra_lookback         INTEGER,
    extra_percentage       NUMERIC(5,2),
    rationale              TEXT NOT NULL,
    created_by             UUID NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_trainprov_sampver UNIQUE (rule_id, version_no),
    CONSTRAINT ck_trainprov_sampver_basis CHECK (
        (basis = 'ALL' AND percentage IS NULL)
        OR (basis = 'PERCENTAGE' AND percentage IS NOT NULL AND percentage > 0 AND percentage <= 100)),
    CONSTRAINT ck_trainprov_sampver_minmax CHECK (max_count IS NULL OR min_count IS NULL OR max_count >= min_count),
    CONSTRAINT ck_trainprov_sampver_risk CHECK (selection_method <> 'RISK_BASED' OR COALESCE(char_length(btrim(risk_factors)), 0) > 0),
    CONSTRAINT ck_trainprov_sampver_extra CHECK (
        (extra_trigger_queries IS NULL AND extra_lookback IS NULL AND extra_percentage IS NULL)
        OR COALESCE(extra_trigger_queries >= 1 AND extra_lookback >= extra_trigger_queries AND extra_percentage > 0 AND extra_percentage <= 100, FALSE)),
    CONSTRAINT ck_trainprov_sampver_rationale CHECK (COALESCE(char_length(btrim(rationale)), 0) >= 10)
);
CREATE INDEX idx_trainprov_sampver_rule ON trainprov_sampling_rule_versions (tenant_id, rule_id, effective_from);

CREATE TABLE trainprov_sampling_draws (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    draw_no           VARCHAR(30) NOT NULL,
    rule_id           UUID NOT NULL REFERENCES trainprov_sampling_rules(id),
    rule_version_id   UUID NOT NULL REFERENCES trainprov_sampling_rule_versions(id),
    rule_snapshot     TEXT NOT NULL,
    drawn_on          DATE NOT NULL,
    decided_from      DATE NOT NULL,
    decided_to        DATE NOT NULL,
    programme_id      UUID REFERENCES trainprov_programmes(id),
    cohort_id         UUID REFERENCES trainprov_cohorts(id),
    assessment_type   VARCHAR(20),
    seed              BIGINT NOT NULL,
    eligible_count    INTEGER NOT NULL CHECK (eligible_count >= 0),
    required_count    INTEGER NOT NULL CHECK (required_count >= 0),
    selected_count    INTEGER NOT NULL CHECK (selected_count >= 0),
    status            VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'CLOSED', 'VOID')),
    drawn_by          UUID NOT NULL,
    drawn_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    decision          VARCHAR(30) CHECK (decision IN ('RESULTS_ACCEPTED', 'SAMPLE_EXTENDED', 'FURTHER_ACTION_REQUIRED')),
    decision_note     TEXT,
    decided_by        UUID,
    decided_at        TIMESTAMPTZ,
    summary           TEXT,
    void_reason       TEXT,
    void_by           UUID,
    void_at           TIMESTAMPTZ,
    version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_trainprov_samp_draw_no UNIQUE (tenant_id, draw_no),
    CONSTRAINT ck_trainprov_samp_draw_window CHECK (decided_from <= decided_to),
    CONSTRAINT ck_trainprov_samp_draw_counts CHECK (selected_count <= eligible_count),
    CONSTRAINT ck_trainprov_samp_draw_state CHECK (
        (status = 'OPEN' AND decision IS NULL AND decided_by IS NULL AND void_reason IS NULL)
        OR (status = 'CLOSED' AND decision IS NOT NULL AND COALESCE(char_length(btrim(decision_note)), 0) >= 10 AND decided_by IS NOT NULL
            AND decided_at IS NOT NULL AND summary IS NOT NULL AND void_reason IS NULL)
        OR (status = 'VOID' AND COALESCE(char_length(btrim(void_reason)), 0) >= 10 AND void_by IS NOT NULL AND void_at IS NOT NULL))
);
CREATE INDEX idx_trainprov_samp_draw_tenant ON trainprov_sampling_draws (tenant_id, status, drawn_on);

-- The whole eligible population of a draw, one row per attempt, with which were picked and why.
CREATE TABLE trainprov_sampling_draw_items (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    draw_id          UUID NOT NULL REFERENCES trainprov_sampling_draws(id),
    attempt_id       UUID NOT NULL REFERENCES trainprov_assessment_attempts(id),
    assessor_id      UUID NOT NULL REFERENCES trainprov_practitioners(id),
    risk_score       INTEGER NOT NULL DEFAULT 0 CHECK (risk_score >= 0),
    risk_factors     VARCHAR(300),
    selected         BOOLEAN NOT NULL,
    selected_reason  VARCHAR(30) CHECK (selected_reason IN ('ALL', 'RANDOM', 'RISK', 'TARGETED', 'EXTRA_AFTER_POOR')),
    target_note      TEXT,
    skipped_reason   TEXT,
    CONSTRAINT uq_trainprov_samp_item UNIQUE (draw_id, attempt_id),
    CONSTRAINT ck_trainprov_samp_item_selected CHECK (
        (selected = TRUE AND selected_reason IS NOT NULL)
        OR (selected = FALSE AND selected_reason IS NULL AND target_note IS NULL AND skipped_reason IS NULL)),
    CONSTRAINT ck_trainprov_samp_item_target CHECK (
        COALESCE(selected_reason = 'TARGETED', FALSE) = (target_note IS NOT NULL)
        AND (target_note IS NULL OR COALESCE(char_length(btrim(target_note)), 0) >= 10)),
    CONSTRAINT ck_trainprov_samp_item_skipped CHECK (skipped_reason IS NULL OR COALESCE(char_length(btrim(skipped_reason)), 0) >= 10)
);
CREATE INDEX idx_trainprov_samp_item_attempt ON trainprov_sampling_draw_items (tenant_id, attempt_id);

CREATE TABLE trainprov_sampling_actions (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    draw_id       UUID NOT NULL REFERENCES trainprov_sampling_draws(id),
    item_id       UUID REFERENCES trainprov_sampling_draw_items(id),
    description   TEXT NOT NULL,
    owner_name    VARCHAR(200) NOT NULL,
    due_on        DATE,
    status        VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'DONE', 'CANCELLED')),
    closed_on     DATE,
    closed_note   TEXT,
    closed_by     UUID,
    created_by    UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    version       BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_trainprov_samp_action_text CHECK (COALESCE(char_length(btrim(description)), 0) >= 10 AND COALESCE(char_length(btrim(owner_name)), 0) >= 2),
    CONSTRAINT ck_trainprov_samp_action_state CHECK (
        (status = 'OPEN' AND closed_on IS NULL AND closed_note IS NULL AND closed_by IS NULL)
        OR (status IN ('DONE', 'CANCELLED') AND closed_on IS NOT NULL AND COALESCE(char_length(btrim(closed_note)), 0) >= 10 AND closed_by IS NOT NULL))
);
CREATE INDEX idx_trainprov_samp_action_draw ON trainprov_sampling_actions (tenant_id, draw_id, status);
