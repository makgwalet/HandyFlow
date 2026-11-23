-- Device compliance: checks the migration and the SQL the service runs, against a real database.
-- Run from the repo root:  psql -d <db> -v ON_ERROR_STOP=1 -f scripts/device-compliance-check.sql
-- Everything is rolled back.  Needs the security tables, V851 (app_version) and V870 applied.
BEGIN;
DO $$
DECLARE t uuid; g uuid; d1 uuid; d2 uuid; d3 uuid; n int; r record; bad boolean;
BEGIN
    t := gen_random_uuid();
    INSERT INTO tenants (id, name, slug, email, tenant_id) VALUES (t, 'dc-check', 'dc-check-' || t, 'dc@example.test', t);
    -- columns exist and start empty (unknown, not "no")
    INSERT INTO security_devices (id, tenant_id, device_hardware_id, device_name, device_type)
        VALUES (gen_random_uuid(), t, 'hw-1', 'Gate phone', 'SHARED_SITE_DEVICE') RETURNING id INTO d1;
    SELECT os_version, screen_lock_on, rooted, posture_reported_at INTO r FROM security_devices WHERE id = d1;
    IF r.os_version IS NOT NULL OR r.screen_lock_on IS NOT NULL OR r.rooted IS NOT NULL OR r.posture_reported_at IS NOT NULL THEN RAISE EXCEPTION 'posture should start unknown'; END IF;

    -- policy: bad values are refused by the database too
    bad := false; BEGIN INSERT INTO security_device_policy (tenant_id, min_os_major) VALUES (t, 0); EXCEPTION WHEN check_violation THEN bad := true; END;
    IF NOT bad THEN RAISE EXCEPTION 'os major 0 accepted'; END IF;
    bad := false; BEGIN INSERT INTO security_device_policy (tenant_id, min_os_major) VALUES (t, 100); EXCEPTION WHEN check_violation THEN bad := true; END;
    IF NOT bad THEN RAISE EXCEPTION 'os major 100 accepted'; END IF;
    bad := false; BEGIN INSERT INTO security_device_policy (tenant_id, min_app_version) VALUES (t, '2.x'); EXCEPTION WHEN check_violation THEN bad := true; END;
    IF NOT bad THEN RAISE EXCEPTION 'app version 2.x accepted'; END IF;
    bad := false; BEGIN INSERT INTO security_device_policy (tenant_id, min_app_version) VALUES (t, '1.2.3.4.5'); EXCEPTION WHEN check_violation THEN bad := true; END;
    IF NOT bad THEN RAISE EXCEPTION 'five-part version accepted'; END IF;

    -- the service's upsert: insert, then update the same row
    INSERT INTO security_device_policy (tenant_id, min_app_version, min_os_major, require_screen_lock, block_rooted, updated_by, updated_at)
        VALUES (t, '2.4', 12, true, true, NULL, now())
    ON CONFLICT (tenant_id) DO UPDATE SET min_app_version = EXCLUDED.min_app_version, min_os_major = EXCLUDED.min_os_major,
        require_screen_lock = EXCLUDED.require_screen_lock, block_rooted = EXCLUDED.block_rooted, updated_by = EXCLUDED.updated_by, updated_at = now();
    INSERT INTO security_device_policy (tenant_id, min_app_version, min_os_major, require_screen_lock, block_rooted, updated_by, updated_at)
        VALUES (t, NULL, NULL, false, false, NULL, now())
    ON CONFLICT (tenant_id) DO UPDATE SET min_app_version = EXCLUDED.min_app_version, min_os_major = EXCLUDED.min_os_major,
        require_screen_lock = EXCLUDED.require_screen_lock, block_rooted = EXCLUDED.block_rooted, updated_by = EXCLUDED.updated_by, updated_at = now();
    SELECT count(*) INTO n FROM security_device_policy WHERE tenant_id = t;
    IF n <> 1 THEN RAISE EXCEPTION 'expected one policy row, got %', n; END IF;
    SELECT min_app_version, min_os_major INTO r FROM security_device_policy WHERE tenant_id = t;
    IF r.min_app_version IS NOT NULL OR r.min_os_major IS NOT NULL THEN RAISE EXCEPTION 'second save did not clear the policy'; END IF;

    -- the phone's posture update never blanks what is known
    UPDATE security_devices SET os_version = COALESCE('14', os_version), screen_lock_on = COALESCE(true, screen_lock_on), rooted = COALESCE(false, rooted), posture_reported_at = now() WHERE id = d1 AND tenant_id = t;
    UPDATE security_devices SET os_version = COALESCE(NULL, os_version), screen_lock_on = COALESCE(NULL, screen_lock_on), rooted = COALESCE(true, rooted), posture_reported_at = now() WHERE id = d1 AND tenant_id = t;
    SELECT os_version, screen_lock_on, rooted INTO r FROM security_devices WHERE id = d1;
    IF r.os_version <> '14' OR r.screen_lock_on IS NOT TRUE OR r.rooted IS NOT TRUE THEN RAISE EXCEPTION 'partial report blanked or lost a value: %', r; END IF;

    -- the report query returns only this company's ACTIVE phones, with the guard's name when there is one
    INSERT INTO security_devices (id, tenant_id, device_hardware_id, device_name, device_type, status) VALUES (gen_random_uuid(), t, 'hw-2', 'Old phone', 'SHARED_SITE_DEVICE', 'LOST') RETURNING id INTO d2;
    SELECT count(*) INTO n FROM (SELECT d.app_version FROM security_devices d LEFT JOIN security_guards g ON g.id = d.guard_id AND g.tenant_id = d.tenant_id
        WHERE d.tenant_id = t AND d.status = 'ACTIVE') x;
    IF n <> 1 THEN RAISE EXCEPTION 'report should see 1 active phone, saw %', n; END IF;
END $$;
ROLLBACK;
