-- Row-level security for the security module: a second wall behind the tenant filters in the code.
-- If a query ever forgets "tenant_id = ?", the database still refuses to show or change another company's rows.
--
-- It is safe to roll out in three steps, each of which can be undone:
--   1. This migration. Policies exist, but while the connection has not said which company it serves, every row is still allowed.
--      Nothing changes for the running system.
--   2. Switch on handyflow.rls.session-tenant (application setting). Each connection then tells the database its company, and the database
--      enforces it. Rows of other companies disappear for that connection.
--   3. Once nothing runs without a company, run  SELECT hf_rls_set_strict(true);
--      A connection with no company then sees nothing. Undo with  SELECT hf_rls_set_strict(false);
--
-- A job that must look across companies sets  app.rls_bypass = 'on'  for its connection (the application does this through TenantSession.system).

CREATE TABLE IF NOT EXISTS hf_rls_config (
    only_row BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (only_row),
    strict   BOOLEAN NOT NULL DEFAULT FALSE,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO hf_rls_config (only_row, strict) VALUES (TRUE, FALSE) ON CONFLICT (only_row) DO NOTHING;
GRANT SELECT ON hf_rls_config TO PUBLIC;

CREATE OR REPLACE FUNCTION hf_rls_strict() RETURNS BOOLEAN LANGUAGE sql STABLE AS
$$ SELECT COALESCE((SELECT strict FROM hf_rls_config WHERE only_row), FALSE) $$;

CREATE OR REPLACE FUNCTION hf_rls_set_strict(on_off BOOLEAN) RETURNS VOID LANGUAGE sql AS
$$ UPDATE hf_rls_config SET strict = on_off, changed_at = now() WHERE only_row $$;

-- The one rule every policy uses. A company that is not a valid UUID makes the statement fail (closed), never open.
CREATE OR REPLACE FUNCTION hf_rls_allows(row_tenant UUID) RETURNS BOOLEAN LANGUAGE sql STABLE AS
$$ SELECT CASE
        WHEN current_setting('app.rls_bypass', true) = 'on' THEN TRUE
        WHEN NULLIF(current_setting('app.tenant_id', true), '') IS NOT NULL THEN row_tenant = current_setting('app.tenant_id', true)::uuid
        ELSE NOT hf_rls_strict()
   END $$;

-- Puts a tenant policy on every table with a tenant_id whose name starts with the prefix. Safe to run again, and later migrations that add a
-- security table should call it:  SELECT hf_rls_apply();
CREATE OR REPLACE FUNCTION hf_rls_apply(prefix TEXT DEFAULT 'security\_') RETURNS INTEGER LANGUAGE plpgsql AS $fn$
DECLARE r RECORD; n INTEGER := 0;
BEGIN
    FOR r IN SELECT c.relname FROM pg_class c JOIN pg_namespace ns ON ns.oid = c.relnamespace
             JOIN pg_attribute a ON a.attrelid = c.oid AND a.attname = 'tenant_id' AND NOT a.attisdropped
             WHERE ns.nspname = current_schema() AND c.relkind IN ('r', 'p') AND c.relname LIKE prefix || '%'
             ORDER BY c.relname LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', r.relname);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', r.relname);
        EXECUTE format('DROP POLICY IF EXISTS hf_tenant_isolation ON %I', r.relname);
        EXECUTE format('CREATE POLICY hf_tenant_isolation ON %I USING (hf_rls_allows(tenant_id)) WITH CHECK (hf_rls_allows(tenant_id))', r.relname);
        n := n + 1;
    END LOOP;
    RETURN n;
END $fn$;

-- What is and is not covered, for the admin to read. covered = security-prefixed table with a tenant_id, RLS on, forced, and the policy in place.
CREATE OR REPLACE VIEW hf_rls_coverage AS
SELECT c.relname AS table_name,
       EXISTS (SELECT 1 FROM pg_attribute a WHERE a.attrelid = c.oid AND a.attname = 'tenant_id' AND NOT a.attisdropped) AS has_tenant_id,
       c.relrowsecurity AS rls_enabled,
       c.relforcerowsecurity AS rls_forced,
       EXISTS (SELECT 1 FROM pg_policy p WHERE p.polrelid = c.oid AND p.polname = 'hf_tenant_isolation') AS has_policy
FROM pg_class c JOIN pg_namespace ns ON ns.oid = c.relnamespace
WHERE ns.nspname = current_schema() AND c.relkind IN ('r', 'p') AND c.relname LIKE 'security\_%';
GRANT SELECT ON hf_rls_coverage TO PUBLIC;

SELECT hf_rls_apply();
