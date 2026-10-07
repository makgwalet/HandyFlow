-- Clinic question library (Q-1, Q-9): reusable, versioned question groups composed into visit types.
-- Workflow configuration only: rules reveal, require, enable, warn or open another group. Nothing here diagnoses.
-- Governance (DEC-CLINIC-001): only ACTIVE content inside its effective dates is served to clinicians.
-- tenant_id NULL = platform-provided content; a tenant row with the same code overrides it for that tenant.

CREATE TABLE clinic_question_group (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID         REFERENCES tenants(id),
    code                  VARCHAR(60)  NOT NULL,
    version               INTEGER      NOT NULL DEFAULT 1,
    name                  VARCHAR(150) NOT NULL,
    category              VARCHAR(60),
    applicable_min_age_months INTEGER,
    applicable_max_age_months INTEGER,
    applicable_sex        TEXT[],                 -- NULL = all
    applicable_visit_types TEXT[],                -- NULL = any visit type
    default_enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order            INTEGER      NOT NULL DEFAULT 0,
    -- governance
    status                VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    clinical_source       TEXT,
    source_version        VARCHAR(60),
    reviewed_by           UUID         REFERENCES users(id),
    reviewed_at           TIMESTAMP,
    approved_by           UUID         REFERENCES users(id),
    approved_at           TIMESTAMP,
    effective_from        DATE,
    effective_to          DATE,
    is_demo               BOOLEAN      NOT NULL DEFAULT FALSE,   -- synthetic content, never clinical advice
    created_at            TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_qgroup_status CHECK (status IN
        ('DRAFT','CLINICAL_REVIEW','CHANGES_REQUESTED','APPROVED','ACTIVE','DEPRECATED','RETIRED')),
    CONSTRAINT chk_qgroup_ages CHECK (applicable_min_age_months IS NULL OR applicable_max_age_months IS NULL
        OR applicable_min_age_months <= applicable_max_age_months),
    -- ACTIVE content must have been approved by a named person
    CONSTRAINT chk_qgroup_active_approved CHECK (status <> 'ACTIVE' OR (approved_by IS NOT NULL AND approved_at IS NOT NULL))
);
CREATE UNIQUE INDEX uq_qgroup_code_version
    ON clinic_question_group (COALESCE(tenant_id, '00000000-0000-0000-0000-000000000000'::uuid), code, version);
CREATE INDEX idx_qgroup_active ON clinic_question_group (tenant_id, status) WHERE status = 'ACTIVE';

CREATE TABLE clinic_question (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id         UUID         NOT NULL REFERENCES clinic_question_group(id) ON DELETE CASCADE,
    code             VARCHAR(60)  NOT NULL,
    label            VARCHAR(300) NOT NULL,
    help_text        TEXT,
    answer_type      VARCHAR(20)  NOT NULL,
    options          JSONB,                       -- [{"value": "...", "label": "..."}] for selects, or allowed units
    min_value        NUMERIC(12,3),
    max_value        NUMERIC(12,3),
    default_visible  BOOLEAN      NOT NULL DEFAULT TRUE,
    default_required BOOLEAN      NOT NULL DEFAULT FALSE,
    observation_code VARCHAR(30),                 -- links a measurement to an observation type (later)
    sort_order       INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_question_code UNIQUE (group_id, code),
    CONSTRAINT chk_question_type CHECK (answer_type IN
        ('YES_NO','SINGLE_SELECT','MULTI_SELECT','RADIO_GROUP','CHECKLIST','TOGGLE','NUMBER','DECIMAL',
         'TEXT','LONG_TEXT','DATE','DATE_TIME','DURATION','MEASUREMENT','SCALE','BODY'))
);

CREATE TABLE clinic_question_rule (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id       UUID        NOT NULL REFERENCES clinic_question(id) ON DELETE CASCADE,
    kind              VARCHAR(20) NOT NULL,
    expression        JSONB       NOT NULL,       -- see QuestionRuleEngine for the format
    message           TEXT,
    target_group_code VARCHAR(60),                -- for TRIGGER_GROUP
    CONSTRAINT chk_rule_kind CHECK (kind IN ('SHOW_WHEN','REQUIRED_WHEN','ENABLE_WHEN','WARNING_WHEN','TRIGGER_GROUP')),
    CONSTRAINT chk_rule_trigger_target CHECK (kind <> 'TRIGGER_GROUP' OR target_group_code IS NOT NULL)
);

-- Red flags are a separate layer from ordinary rules (Q-5): a prompt to the clinician, never a diagnosis.
CREATE TABLE clinic_red_flag_rule (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id    UUID         NOT NULL REFERENCES clinic_question_group(id) ON DELETE CASCADE,
    code        VARCHAR(60)  NOT NULL,
    label       VARCHAR(200) NOT NULL,
    severity    VARCHAR(10)  NOT NULL,
    expression  JSONB        NOT NULL,
    message     TEXT,
    CONSTRAINT uq_red_flag_code UNIQUE (group_id, code),
    CONSTRAINT chk_red_flag_severity CHECK (severity IN ('INFO','URGENT'))
);

-- Which groups a visit type opens, in order. tenant_id NULL = platform default.
CREATE TABLE clinic_visit_type_group (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID         REFERENCES tenants(id),
    visit_type  VARCHAR(50)  NOT NULL,
    group_code  VARCHAR(60)  NOT NULL,
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    required    BOOLEAN      NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX uq_visit_type_group
    ON clinic_visit_type_group (COALESCE(tenant_id, '00000000-0000-0000-0000-000000000000'::uuid), visit_type, group_code);

-- Answers live on the consultation: {"groups": {"<groupCode>": {"version": 1, "answers": {"<questionCode>": value}}}}
ALTER TABLE clinic_consultations ADD COLUMN form_data JSONB;
