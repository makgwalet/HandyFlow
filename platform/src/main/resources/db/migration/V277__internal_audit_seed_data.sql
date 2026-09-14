-- V277__internal_audit_seed_data.sql
-- Demo data for Internal Audit, so the module has something real to
-- explore rather than an empty screen. Scoped to the 'zeta-earthmoving'
-- test tenant only, matching the exact established pattern from V40's
-- own tenant_modules grant (INSERT ... SELECT ... WHERE t.slug = ... ON
-- CONFLICT DO NOTHING) — a safe no-op everywhere else, including any
-- real tenant's database this migration also runs against.
--
-- Looks up the tenant and an admin user via SELECT rather than
-- hardcoding UUIDs this migration has no way to actually know — if
-- either lookup comes back empty, the DO block exits early and inserts
-- nothing, rather than failing or inserting rows with a null/wrong
-- owner.
--
-- The four universe entries and their risk inputs are deliberately the
-- exact worked example from the product owner's own design conversation
-- (Payroll/Fuel/Petty Cash/Procurement, with the same transaction-
-- volume/prior-findings/last-audit characteristics) — reproduced here
-- as seed data specifically so the system's own risk calculation can be
-- checked against the example that motivated the hybrid design in the
-- first place. Petty Cash and Fuel are seeded into the universe but
-- deliberately left out of the annual plan and left unassessed for risk
-- — only Payroll and Procurement (the two "High" entries) get the full
-- treatment through to an engagement, keeping the demo focused rather
-- than exhaustively populating every table for its own sake.

DO $$
DECLARE
    v_tenant_id UUID;
    v_user_id   UUID;
    v_payroll_id UUID;
    v_fuel_id UUID;
    v_petty_cash_id UUID;
    v_procurement_id UUID;
    v_payroll_risk_id UUID;
    v_procurement_risk_id UUID;
    v_plan_id UUID;
    v_payroll_plan_entry_id UUID;
    v_procurement_plan_entry_id UUID;
    v_engagement_id UUID;
BEGIN
    SELECT id INTO v_tenant_id FROM tenants WHERE slug = 'zeta-earthmoving';
    IF v_tenant_id IS NULL THEN
        RAISE NOTICE 'Internal Audit seed data skipped — no tenant with slug zeta-earthmoving in this database';
        RETURN;
    END IF;

    SELECT u.id INTO v_user_id
    FROM users u
    JOIN user_roles ur ON ur.user_id = u.id
    JOIN roles r ON r.id = ur.role_id AND r.tenant_id = v_tenant_id
    WHERE u.tenant_id = v_tenant_id AND r.name = 'ADMIN'
    ORDER BY u.created_at
    LIMIT 1;
    IF v_user_id IS NULL THEN
        RAISE NOTICE 'Internal Audit seed data skipped — no ADMIN user found for tenant zeta-earthmoving';
        RETURN;
    END IF;

    -- Idempotent re-run guard — if Payroll already exists for this
    -- tenant (this migration having already run), skip entirely rather
    -- than creating duplicates on a re-run in a dev environment.
    IF EXISTS (SELECT 1 FROM audit_universe_entries WHERE tenant_id = v_tenant_id AND name = 'Payroll') THEN
        RAISE NOTICE 'Internal Audit seed data skipped — already seeded for tenant zeta-earthmoving';
        RETURN;
    END IF;

    -- ── Universe (the product owner's own worked example) ──────────────────
    v_payroll_id := gen_random_uuid();
    INSERT INTO audit_universe_entries (id, tenant_id, name, description, process_area, last_audit_date, active, created_by, created_at)
    VALUES (v_payroll_id, v_tenant_id, 'Payroll', 'Monthly payroll processing, statutory deductions, and payments.', 'Payroll',
            CURRENT_DATE - INTERVAL '3 years', true, v_user_id, NOW());

    v_fuel_id := gen_random_uuid();
    INSERT INTO audit_universe_entries (id, tenant_id, name, description, process_area, last_audit_date, active, created_by, created_at)
    VALUES (v_fuel_id, v_tenant_id, 'Fuel', 'Fuel receipts, dispatches, and tank reconciliation.', 'Fuel',
            CURRENT_DATE - INTERVAL '1 year', true, v_user_id, NOW());

    v_petty_cash_id := gen_random_uuid();
    INSERT INTO audit_universe_entries (id, tenant_id, name, description, process_area, last_audit_date, active, created_by, created_at)
    VALUES (v_petty_cash_id, v_tenant_id, 'Petty Cash', 'Small-value cash disbursements and reconciliation.', 'Petty Cash',
            CURRENT_DATE - INTERVAL '6 months', true, v_user_id, NOW());

    v_procurement_id := gen_random_uuid();
    INSERT INTO audit_universe_entries (id, tenant_id, name, description, process_area, last_audit_date, active, created_by, created_at)
    VALUES (v_procurement_id, v_tenant_id, 'Procurement', 'Purchase orders, goods receipts, and three-way matching.', 'Procurement',
            CURRENT_DATE - INTERVAL '2 years', true, v_user_id, NOW());

    -- ── Risk assessments — Payroll and Procurement only ─────────────────────
    -- Component scores chosen so RiskAssessment's own calculateLevel()
    -- reproduces the product owner's own "System risk: High" for both,
    -- given their actual timeSinceLastAuditScore() (3 years -> 4, 2
    -- years -> 3) computed from last_audit_date above.
    v_payroll_risk_id := gen_random_uuid();
    INSERT INTO audit_risk_assessments (id, tenant_id, universe_entry_id, inherent_risk_score, control_risk_score,
        historical_findings_score, time_since_last_audit_score, business_regulatory_impact_score,
        system_calculated_risk, final_audit_risk, assessed_by, assessed_at)
    VALUES (v_payroll_risk_id, v_tenant_id, v_payroll_id, 4, 4, 4, 4, 3, 'HIGH', 'HIGH', v_user_id, NOW());

    v_procurement_risk_id := gen_random_uuid();
    INSERT INTO audit_risk_assessments (id, tenant_id, universe_entry_id, inherent_risk_score, control_risk_score,
        historical_findings_score, time_since_last_audit_score, business_regulatory_impact_score,
        system_calculated_risk, final_audit_risk, assessed_by, assessed_at)
    VALUES (v_procurement_risk_id, v_tenant_id, v_procurement_id, 4, 4, 4, 3, 3, 'HIGH', 'HIGH', v_user_id, NOW());

    -- ── Annual plan, selecting the two High-risk entries ────────────────────
    v_plan_id := gen_random_uuid();
    INSERT INTO audit_annual_plans (id, tenant_id, plan_year, status, created_by, created_at)
    VALUES (v_plan_id, v_tenant_id, EXTRACT(YEAR FROM CURRENT_DATE)::INT, 'DRAFT', v_user_id, NOW());

    v_payroll_plan_entry_id := gen_random_uuid();
    INSERT INTO audit_plan_entries (id, tenant_id, plan_id, universe_entry_id, risk_assessment_id, planned_quarter, rationale, status, created_at)
    VALUES (v_payroll_plan_entry_id, v_tenant_id, v_plan_id, v_payroll_id, v_payroll_risk_id, 1,
            'High risk score — high transaction volume, five prior findings, three years since last audit.', 'PLANNED', NOW());

    v_procurement_plan_entry_id := gen_random_uuid();
    INSERT INTO audit_plan_entries (id, tenant_id, plan_id, universe_entry_id, risk_assessment_id, planned_quarter, rationale, status, created_at)
    VALUES (v_procurement_plan_entry_id, v_tenant_id, v_plan_id, v_procurement_id, v_procurement_risk_id, 2,
            'High risk score — high transaction volume, four prior findings, two years since last audit.', 'PLANNED', NOW());

    -- ── One engagement against the Payroll plan entry ───────────────────────
    v_engagement_id := gen_random_uuid();
    INSERT INTO audit_engagements (id, tenant_id, plan_entry_id, universe_entry_id, name, status, start_date, end_date, created_by, created_at)
    VALUES (v_engagement_id, v_tenant_id, v_payroll_plan_entry_id, v_payroll_id,
            EXTRACT(YEAR FROM CURRENT_DATE)::TEXT || ' Payroll Audit', 'PLANNING',
            CURRENT_DATE, CURRENT_DATE + INTERVAL '30 days', v_user_id, NOW());

    INSERT INTO audit_engagement_assignments (id, tenant_id, engagement_id, user_id, role, assigned_by, assigned_at)
    VALUES (gen_random_uuid(), v_tenant_id, v_engagement_id, v_user_id, 'HEAD_OF_INTERNAL_AUDIT', v_user_id, NOW());

    RAISE NOTICE 'Internal Audit seed data created for tenant zeta-earthmoving (engagement %)', v_engagement_id;
END $$;
