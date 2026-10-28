-- Substring search over applicants and jobs. Trigram indexes need the pg_trgm extension; where the database user may not create it, search still works
-- (it just scans), so the indexes are skipped rather than failing the migration.
DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS pg_trgm;
    CREATE INDEX IF NOT EXISTS idx_rec_applicants_name_trgm  ON rec_applicants USING gin (lower(first_name || ' ' || last_name) gin_trgm_ops);
    CREATE INDEX IF NOT EXISTS idx_rec_applicants_email_trgm ON rec_applicants USING gin (lower(email) gin_trgm_ops);
    CREATE INDEX IF NOT EXISTS idx_rec_jobs_title_trgm       ON rec_jobs USING gin (lower(title) gin_trgm_ops);
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'pg_trgm indexes skipped: %', SQLERRM;
END $$;
CREATE INDEX IF NOT EXISTS idx_rec_applications_tenant_stage ON rec_applications (tenant_id, stage, applied_at DESC);
