-- Guard screening: request details, sign-off decision, and the two extra verification types.
-- Evidence files are stored through the shared evidence module (source_module 'security',
-- entity type 'GuardScreeningRecord'), so no new file table is needed.

ALTER TABLE security_guard_screening_records DROP CONSTRAINT IF EXISTS security_guard_screening_records_screening_type_check;
ALTER TABLE security_guard_screening_records
    ADD CONSTRAINT security_guard_screening_records_screening_type_check
    CHECK (screening_type IN (
        'POLYGRAPH', 'CRIMINAL_RECORD_CHECK', 'REFERENCE_CHECK',
        'DRUG_TEST', 'PSYCHOMETRIC', 'CREDIT_CHECK',
        'ID_VERIFICATION', 'QUALIFICATION_VERIFICATION', 'OTHER'));

ALTER TABLE security_guard_screening_records
    ADD COLUMN IF NOT EXISTS provider         VARCHAR(200),
    ADD COLUMN IF NOT EXISTS requested_at     DATE,
    ADD COLUMN IF NOT EXISTS decision         VARCHAR(20),
    ADD COLUMN IF NOT EXISTS decision_note    TEXT,
    ADD COLUMN IF NOT EXISTS decided_by       UUID,
    ADD COLUMN IF NOT EXISTS decided_by_name  VARCHAR(200),
    ADD COLUMN IF NOT EXISTS decided_at       TIMESTAMPTZ;

ALTER TABLE security_guard_screening_records
    ADD CONSTRAINT security_guard_screening_decision_check
    CHECK (decision IS NULL OR decision IN ('CLEARED', 'NOT_CLEARED'));
