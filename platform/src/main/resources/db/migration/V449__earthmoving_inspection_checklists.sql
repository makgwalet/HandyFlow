-- Versioned inspection checklists, one set per company and inspection type. A version is a DRAFT while it is being written and is
-- never changed after it is PUBLISHED: a change is a new version. An inspection records the version it was answered against, so an
-- old inspection always shows the questions that were asked at the time. A company with no published version uses the built-in
-- walk-round, which stays the minimum for the pre-use, handover and return types.
CREATE TABLE IF NOT EXISTS earthmoving_checklist_versions (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    inspection_type  VARCHAR(12) NOT NULL,
    version_no       INTEGER NOT NULL CHECK (version_no >= 1),
    status           VARCHAR(10) NOT NULL DEFAULT 'DRAFT',
    effective_from   DATE,
    requires_signoff BOOLEAN NOT NULL DEFAULT FALSE,
    note             VARCHAR(300),
    created_by       UUID,
    created_at       TIMESTAMP NOT NULL DEFAULT now(),
    published_by     UUID,
    published_at     TIMESTAMP,
    CONSTRAINT chk_em_chk_ver_type CHECK (inspection_type IN ('PRE_USE', 'HANDOVER', 'RETURN', 'MAINTENANCE', 'SAFETY')),
    CONSTRAINT chk_em_chk_ver_status CHECK (status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT chk_em_chk_ver_pub CHECK (status = 'DRAFT' OR effective_from IS NOT NULL)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_em_chk_ver_no ON earthmoving_checklist_versions (tenant_id, inspection_type, version_no);
CREATE UNIQUE INDEX IF NOT EXISTS uq_em_chk_ver_draft ON earthmoving_checklist_versions (tenant_id, inspection_type) WHERE status = 'DRAFT';

CREATE TABLE IF NOT EXISTS earthmoving_checklist_questions (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL,
    version_id   UUID NOT NULL REFERENCES earthmoving_checklist_versions(id),
    position     INTEGER NOT NULL,
    section      VARCHAR(60),
    text         VARCHAR(80) NOT NULL,
    answer_type  VARCHAR(10) NOT NULL DEFAULT 'PASS_FAIL',
    required     BOOLEAN NOT NULL DEFAULT TRUE,
    built_in     BOOLEAN NOT NULL DEFAULT FALSE,
    min_value    NUMERIC(14,3),
    max_value    NUMERIC(14,3),
    unit         VARCHAR(12),
    fail_outcome VARCHAR(8) NOT NULL DEFAULT 'DEFECT',
    CONSTRAINT chk_em_chk_q_type CHECK (answer_type IN ('PASS_FAIL', 'NUMERIC', 'TEXT', 'PHOTO')),
    CONSTRAINT chk_em_chk_q_fail CHECK (fail_outcome IN ('DEFECT', 'UNSAFE')),
    CONSTRAINT chk_em_chk_q_range CHECK (min_value IS NULL OR max_value IS NULL OR min_value <= max_value)
);
CREATE INDEX IF NOT EXISTS idx_em_chk_q_version ON earthmoving_checklist_questions (version_id, position);

-- Inspections: the other two kinds of walk-round, and the version and sign-off each one was done against.
ALTER TABLE earthmoving_inspections DROP CONSTRAINT IF EXISTS chk_em_inspection_type;
ALTER TABLE earthmoving_inspections ADD CONSTRAINT chk_em_inspection_type
    CHECK (inspection_type IN ('PRE_USE', 'HANDOVER', 'RETURN', 'MAINTENANCE', 'SAFETY'));
ALTER TABLE earthmoving_inspections ADD COLUMN IF NOT EXISTS template_version_id UUID;
ALTER TABLE earthmoving_inspections ADD COLUMN IF NOT EXISTS template_version_no INTEGER;
ALTER TABLE earthmoving_inspections ADD COLUMN IF NOT EXISTS sign_off_name VARCHAR(100);

-- Inspection lines: what kind of question it was and the value given. Existing lines were all pass/fail.
ALTER TABLE earthmoving_inspection_items ADD COLUMN IF NOT EXISTS section VARCHAR(60);
ALTER TABLE earthmoving_inspection_items ADD COLUMN IF NOT EXISTS answer_type VARCHAR(10) NOT NULL DEFAULT 'PASS_FAIL';
ALTER TABLE earthmoving_inspection_items ADD COLUMN IF NOT EXISTS value_number NUMERIC(14,3);
ALTER TABLE earthmoving_inspection_items ADD COLUMN IF NOT EXISTS value_text TEXT;
ALTER TABLE earthmoving_inspection_items ADD COLUMN IF NOT EXISTS evidence_ref VARCHAR(200);
ALTER TABLE earthmoving_inspection_items ADD COLUMN IF NOT EXISTS unit VARCHAR(12);
