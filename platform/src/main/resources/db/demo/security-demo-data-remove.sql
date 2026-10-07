-- =============================================================================
-- HandyFlow Security module: REMOVE DEMO DATA
--
-- Deletes exactly the rows created by security-demo-data.sql, found by their id prefix d3d3d3d3-.
-- Nothing else is touched, so real data on the same tenant is safe. It needs no arguments:
--   psql -h localhost -U handyflow -d handyflow -f security-demo-data-remove.sql
-- Docker (PowerShell):
--   Get-Content security-demo-data-remove.sql | docker exec -i handyflow-db psql -U handyflow -d handyflow
--
-- Tables are listed child first so foreign keys never block the delete. It is safe to run twice.
-- =============================================================================
\set ON_ERROR_STOP on
BEGIN;

DO $$
DECLARE
  t text;
  n bigint;
  total bigint := 0;
BEGIN
  FOR t IN SELECT unnest(ARRAY[
  'security_advance_surveys',
  'security_itinerary_stops',
  'security_detail_assignments',
  'security_protection_details',
  'security_protection_vehicles',
  'security_declined_principals',
  'security_principal_vetting',
  'security_principals',
  'security_payroll_line_items',
  'security_payroll_periods',
  'security_guard_rate_history',
  'security_dispatches',
  'security_alarm_events',
  'security_cameras',
  'security_shift_swap_requests',
  'security_rotation_assignments',
  'security_rotation_patterns',
  'security_resource_custody',
  'security_armoury_logs',
  'security_armoury',
  'security_post_order_acknowledgements',
  'security_post_orders',
  'security_audit_log',
  'security_report_runs',
  'security_gate_register_entries',
  'security_access_points',
  'security_incident_events',
  'security_incidents',
  'security_guard_complaint_events',
  'security_guard_complaints',
  'security_guard_ratings',
  'security_guard_documents',
  'security_guard_competencies',
  'security_guard_screening_records',
  'security_risk_settings',
  'evidence',
  'security_guard_current_location',
  'security_guard_location_pings',
  'security_checkpoint_logs',
  'security_patrol_rounds',
  'security_device_sessions',
  'security_devices',
  'security_patrol_route_checkpoints',
  'security_patrol_routes',
  'security_checkpoints',
  'security_shifts',
  'security_grade_rates',
  'security_branch_assignments',
  'security_guard_score_history',
  'security_guard_reviews',
  'security_guards',
  'security_posts',
  'security_contacts',
  'security_sites',
  'security_branches'
  ]) LOOP
    IF t IN ('security_guard_current_location', 'security_guard_score_history') THEN
      -- keyed by guard: the nightly job writes these rows with random ids
      EXECUTE format('DELETE FROM %I WHERE guard_id::text LIKE ''d3d3d3d3-%%''', t);
    ELSE
      EXECUTE format('DELETE FROM %I WHERE id::text LIKE ''d3d3d3d3-%%''', t);
    END IF;
    GET DIAGNOSTICS n = ROW_COUNT;
    total := total + n;
  END LOOP;
  -- Evidence files are stored separately from the evidence rows.
  DELETE FROM stored_files WHERE storage_key LIKE '%/d3d3d3d3-%-demo.pdf';
  GET DIAGNOSTICS n = ROW_COUNT;
  total := total + n;
  RAISE NOTICE 'Removed % demo rows.', total;
END $$;

COMMIT;
