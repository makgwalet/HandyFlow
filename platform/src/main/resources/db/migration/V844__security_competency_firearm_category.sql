-- SEC chunk 15: firearm competency per category (SAPS competency is issued per firearm type).
ALTER TABLE security_guard_competencies
    ADD COLUMN IF NOT EXISTS firearm_category VARCHAR(20)
        CHECK (firearm_category IS NULL OR firearm_category IN ('HANDGUN', 'SHOTGUN', 'RIFLE'));

COMMENT ON COLUMN security_guard_competencies.firearm_category IS
    'Only for FIREARM_COMPETENCY. NULL on older rows: they count for every category until strict mode is on.';
