-- Agriculture chunk 29: a recorded birth creates the offspring. Temporary tags, tag history, species reminder setting.

ALTER TABLE ag_animals ADD COLUMN IF NOT EXISTS tag_temporary BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE ag_animals ADD COLUMN IF NOT EXISTS birth_record_id UUID;
ALTER TABLE ag_animals ADD COLUMN IF NOT EXISTS tag_reminder_notified_on DATE;

-- Days after birth to remind the farmer to fit the permanent tag. Empty means no reminder. No legal number is assumed.
ALTER TABLE ag_species ADD COLUMN IF NOT EXISTS tag_reminder_days INTEGER;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_ag_species_tag_reminder_days') THEN
        ALTER TABLE ag_species ADD CONSTRAINT chk_ag_species_tag_reminder_days
            CHECK (tag_reminder_days IS NULL OR (tag_reminder_days BETWEEN 1 AND 365)) NOT VALID;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_ag_animals_temp_tag ON ag_animals (tenant_id) WHERE tag_temporary AND deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_ag_animals_birth_record ON ag_animals (tenant_id, birth_record_id) WHERE birth_record_id IS NOT NULL;

-- Every tag an animal has had before its current one.
CREATE TABLE IF NOT EXISTS ag_animal_tag_history (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    animal_id       UUID         NOT NULL,
    old_tag         VARCHAR(100) NOT NULL,
    new_tag         VARCHAR(100) NOT NULL,
    changed_by      UUID,
    changed_by_name VARCHAR(255),
    changed_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT chk_ag_animal_tag_history_differ CHECK (old_tag <> new_tag)
);
CREATE INDEX IF NOT EXISTS idx_ag_animal_tag_history_animal ON ag_animal_tag_history (tenant_id, animal_id, changed_at DESC);
