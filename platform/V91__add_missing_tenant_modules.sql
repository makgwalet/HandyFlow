-- V91 — Seed projects, ap, accountant into the module catalogue and activate for tenant
--
-- Root cause:
--   - GET /api/v1/billing/modules returns the module catalogue table
--   - GET /api/v1/billing/modules/mine returns module_subscriptions for this tenant
--   - 'projects', 'ap', 'accountant' are absent from BOTH
--   - All other 19 modules are already active (accessible: true) — no action needed for them
--
-- This migration:
--   1. Discovers the catalogue table name dynamically (avoids hardcoding)
--   2. Inserts the 3 missing catalogue rows
--   3. Inserts module_subscriptions rows with correct trial_ends_at (60 days)
--
-- Run:
--   docker cp V91__seed_modules_and_activate_tenant.sql handyflow-db:/tmp/V91.sql
--   docker exec -i handyflow-db psql -U handyflow -d handyflow -f /tmp/V91.sql

DO $$
DECLARE
    v_tenant      UUID := '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f';
    v_tbl         TEXT;
    v_trial_end   TIMESTAMP := now() + INTERVAL '60 days';
    v_col_list    TEXT;
    v_has_trial   BOOLEAN := false;
BEGIN

    -- ── 1. Discover catalogue table ─────────────────────────────────────────
    SELECT c1.table_name INTO v_tbl
    FROM   information_schema.columns c1
    JOIN   information_schema.columns c2
           ON c1.table_name = c2.table_name AND c1.table_schema = c2.table_schema
    JOIN   information_schema.columns c3
           ON c1.table_name = c3.table_name AND c1.table_schema = c3.table_schema
    WHERE  c1.table_schema = 'public'
      AND  c1.column_name  = 'icon'
      AND  c2.column_name  = 'category'
      AND  c3.column_name  = 'sort_order'
      AND  c1.table_name NOT IN ('module_subscriptions', 'plan_modules')
    LIMIT 1;

    IF v_tbl IS NULL THEN
        RAISE EXCEPTION 'Cannot find module catalogue table — check information_schema.columns for icon+category+sort_order';
    END IF;
    RAISE NOTICE 'Catalogue table: %', v_tbl;

    -- ── 2. Insert missing catalogue rows ────────────────────────────────────
    EXECUTE format(
        'INSERT INTO %I (id, key, name, description, monthly_price, currency, icon, category, sort_order)
         VALUES
           ($1, ''projects'',
            ''Project Management'',
            ''Gantt scheduling, task management, resource planning, EVM budget tracking, site diaries, snag lists and client portal. Built for construction, civil engineering and physical-world delivery.'',
            599, ''ZAR'', ''hard-hat'', ''INDUSTRY'', 35),
           ($2, ''ap'',
            ''Accounts Payable'',
            ''Supplier invoice capture, three-way matching against POs and goods receipts, approval workflows and payment tracking.'',
            299, ''ZAR'', ''receipt'', ''FINANCE'', 105),
           ($3, ''accountant'',
            ''Accountant Portal'',
            ''Client portfolio management, SARS submission tracking, financial reporting and billing for accounting practices.'',
            299, ''ZAR'', ''book-open'', ''FINANCE'', 125)
         ON CONFLICT (key) DO NOTHING',
        v_tbl,
        gen_random_uuid(), gen_random_uuid(), gen_random_uuid()
    );
    RAISE NOTICE '✓ Catalogue rows inserted (ON CONFLICT DO NOTHING)';

    -- ── 3. Check whether module_subscriptions has trial_ends_at column ──────
    -- The /modules/mine response shows trialEndsAt — so the table has this column
    -- but V5 schema didn't include it. It was added in a later migration.
    SELECT true INTO v_has_trial
    FROM   information_schema.columns
    WHERE  table_schema = 'public'
      AND  table_name   = 'module_subscriptions'
      AND  column_name  = 'trial_ends_at'
    LIMIT 1;

    -- ── 4. Insert module_subscriptions for the 3 new modules ────────────────
    IF v_has_trial THEN
        RAISE NOTICE 'module_subscriptions has trial_ends_at — inserting with trial';
        INSERT INTO module_subscriptions
               (id, tenant_id, module_key, status, price_cents, activated_at, trial_ends_at)
        VALUES
            (gen_random_uuid(), v_tenant, 'projects',   'TRIAL', 0, now(), v_trial_end),
            (gen_random_uuid(), v_tenant, 'ap',         'TRIAL', 0, now(), v_trial_end),
            (gen_random_uuid(), v_tenant, 'accountant', 'TRIAL', 0, now(), v_trial_end)
        ON CONFLICT (tenant_id, module_key) DO UPDATE
            SET status       = 'TRIAL',
                trial_ends_at = EXCLUDED.trial_ends_at,
                updated_at    = now();
    ELSE
        RAISE NOTICE 'module_subscriptions has no trial_ends_at — inserting with ACTIVE status';
        INSERT INTO module_subscriptions
               (id, tenant_id, module_key, status, price_cents, activated_at)
        VALUES
            (gen_random_uuid(), v_tenant, 'projects',   'ACTIVE', 0, now()),
            (gen_random_uuid(), v_tenant, 'ap',         'ACTIVE', 0, now()),
            (gen_random_uuid(), v_tenant, 'accountant', 'ACTIVE', 0, now())
        ON CONFLICT (tenant_id, module_key) DO UPDATE
            SET status     = 'ACTIVE',
                updated_at = now();
    END IF;

    RAISE NOTICE '✓ module_subscriptions: projects, ap, accountant activated for tenant %', v_tenant;

END $$;

-- ── Verify ────────────────────────────────────────────────────────────────────
SELECT module_key, status,
       activated_at::date,
       trial_ends_at::date,

FROM   module_subscriptions
WHERE  tenant_id = '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f'
  AND  module_key IN ('projects', 'ap', 'accountant')
ORDER  BY module_key;