-- Chunk 2: portal token lifecycle + snag numbering via shared sequence.
-- Rename with your next Flyway version number.

ALTER TABLE projects
    ADD COLUMN IF NOT EXISTS portal_enabled          BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS portal_token_expires_at TIMESTAMPTZ;   -- NULL = never expires (legacy projects)

-- Seed the shared sequence from existing snags so switching FieldService from
-- MAX+1 to TenantSequenceService does NOT restart numbering at SN0001 and
-- collide with snags already in use. GREATEST keeps any value already seeded.
INSERT INTO tenant_number_sequences (tenant_id, sequence_name, last_value, updated_at)
SELECT tenant_id,
       'SNAG:' || project_id::text,
       MAX(CAST(SUBSTRING(snag_number FROM 3) AS int)),
       now()
FROM snag_items
WHERE snag_number ~ '^SN[0-9]+$'
GROUP BY tenant_id, project_id
ON CONFLICT (tenant_id, sequence_name)
DO UPDATE SET last_value = GREATEST(tenant_number_sequences.last_value, EXCLUDED.last_value),
              updated_at = now();

-- OPTIONAL (recommended once clean): enforce uniqueness at the database level.
-- First check for duplicates left behind by the old MAX+1 race:
--   SELECT project_id, snag_number, COUNT(*) FROM snag_items
--   GROUP BY project_id, snag_number HAVING COUNT(*) > 1;
-- If that returns no rows:
--   CREATE UNIQUE INDEX IF NOT EXISTS uq_snag_number_per_project
--       ON snag_items (project_id, snag_number);