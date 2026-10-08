-- Which consultation stages a visit type requires before signing (CLINIC-DEC-012).
-- tenant_id NULL = platform default; a practice's own rows replace the default for that visit type.
-- Visit types with no row anywhere fall back to Symptoms + Diagnosis (the rule from CLINIC-DEC-010).
CREATE TABLE IF NOT EXISTS clinic_visit_type_stage (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID,
    visit_type  VARCHAR(50) NOT NULL,
    stage       VARCHAR(30) NOT NULL,
    required    BOOLEAN     NOT NULL,
    CONSTRAINT chk_visit_stage CHECK (stage IN ('SYMPTOMS','EXAMINATION','DIAGNOSIS','PLAN'))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_visit_stage_platform ON clinic_visit_type_stage (visit_type, stage) WHERE tenant_id IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_visit_stage_tenant   ON clinic_visit_type_stage (tenant_id, visit_type, stage) WHERE tenant_id IS NOT NULL;

INSERT INTO clinic_visit_type_stage (tenant_id, visit_type, stage, required)
SELECT NULL, v.vt, v.st, v.req FROM (VALUES
    ('CONSULTATION','SYMPTOMS',true),  ('CONSULTATION','EXAMINATION',false), ('CONSULTATION','DIAGNOSIS',true), ('CONSULTATION','PLAN',false),
    ('ANTENATAL','SYMPTOMS',true),     ('ANTENATAL','EXAMINATION',true),     ('ANTENATAL','DIAGNOSIS',true),    ('ANTENATAL','PLAN',true),
    ('RESULTS_REVIEW','SYMPTOMS',false),('RESULTS_REVIEW','EXAMINATION',false),('RESULTS_REVIEW','DIAGNOSIS',true),('RESULTS_REVIEW','PLAN',true)
) AS v(vt, st, req)
WHERE NOT EXISTS (SELECT 1 FROM clinic_visit_type_stage x WHERE x.tenant_id IS NULL AND x.visit_type = v.vt AND x.stage = v.st);

-- Overrides can now be recorded for any of the four stages.
ALTER TABLE clinic_consultation_overrides DROP CONSTRAINT IF EXISTS chk_override_step;
ALTER TABLE clinic_consultation_overrides ADD CONSTRAINT chk_override_step
    CHECK (step IN ('SYMPTOMS','EXAMINATION','DIAGNOSIS','PLAN'));
