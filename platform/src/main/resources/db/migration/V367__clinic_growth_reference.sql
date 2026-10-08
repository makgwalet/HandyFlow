-- Growth reference data (CLINIC-DEC-013, 015, 019). The table is deliberately EMPTY: no reference values are shipped.
-- A set is loaded by a content administrator as DRAFT, reviewed, approved and activated by different qualified people.
-- Until a set is ACTIVE the chart shows measurements only, labelled "DATA NOT CLINICALLY APPROVED".

CREATE TABLE IF NOT EXISTS clinic_growth_reference_sets (
    id              UUID         PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    measure         VARCHAR(30)  NOT NULL,
    sex             VARCHAR(10)  NOT NULL,
    title           VARCHAR(200) NOT NULL,
    source          VARCHAR(300) NOT NULL,
    source_version  VARCHAR(60)  NOT NULL,
    min_age_months  NUMERIC(6,2) NOT NULL,
    max_age_months  NUMERIC(6,2) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    created_by      UUID         REFERENCES users(id),
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    reviewed_by     UUID         REFERENCES users(id),
    reviewed_at     TIMESTAMP,
    approved_by     UUID         REFERENCES users(id),
    approved_at     TIMESTAMP,
    CONSTRAINT chk_growth_set_measure CHECK (measure IN ('WEIGHT','HEIGHT','HEAD_CIRCUMFERENCE','BMI')),
    CONSTRAINT chk_growth_set_sex CHECK (sex IN ('MALE','FEMALE')),
    CONSTRAINT chk_growth_set_status CHECK (status IN ('DRAFT','CLINICAL_REVIEW','APPROVED','ACTIVE','RETIRED')),
    CONSTRAINT chk_growth_set_ages CHECK (min_age_months >= 0 AND max_age_months > min_age_months),
    -- ACTIVE needs a named approver and a named reviewer, and they must be different people
    CONSTRAINT chk_growth_set_active CHECK (status <> 'ACTIVE' OR (approved_by IS NOT NULL AND approved_at IS NOT NULL
        AND reviewed_by IS NOT NULL AND approved_by <> reviewed_by))
);

-- one live set per measure and sex
CREATE UNIQUE INDEX IF NOT EXISTS uq_growth_set_active ON clinic_growth_reference_sets (tenant_id, measure, sex) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_growth_set_tenant ON clinic_growth_reference_sets (tenant_id, status);

CREATE TABLE IF NOT EXISTS clinic_growth_reference_points (
    set_id      UUID         NOT NULL REFERENCES clinic_growth_reference_sets(id) ON DELETE CASCADE,
    age_months  NUMERIC(6,2) NOT NULL,
    l_value     NUMERIC(12,6) NOT NULL,
    m_value     NUMERIC(12,6) NOT NULL,
    s_value     NUMERIC(12,6) NOT NULL,
    PRIMARY KEY (set_id, age_months),
    CONSTRAINT chk_growth_point_positive CHECK (m_value > 0 AND s_value > 0)
);

-- Points may only change while the set is a DRAFT.
CREATE OR REPLACE FUNCTION clinic_growth_points_draft_only() RETURNS trigger AS $$
DECLARE st VARCHAR(20);
BEGIN
    SELECT status INTO st FROM clinic_growth_reference_sets WHERE id = COALESCE(NEW.set_id, OLD.set_id);
    IF st IS NOT NULL AND st <> 'DRAFT' THEN
        RAISE EXCEPTION 'Growth reference points can only change while the set is a DRAFT';
    END IF;
    RETURN COALESCE(NEW, OLD);
END $$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_growth_points_draft_only ON clinic_growth_reference_points;
CREATE TRIGGER trg_growth_points_draft_only BEFORE INSERT OR UPDATE OR DELETE ON clinic_growth_reference_points
    FOR EACH ROW EXECUTE FUNCTION clinic_growth_points_draft_only();
