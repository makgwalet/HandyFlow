\set ON_ERROR_STOP on
BEGIN;
CREATE TEMP TABLE r(label text, ok boolean);
INSERT INTO tenants (id, tenant_id, name, slug, email) VALUES ('a0000000-0000-0000-0000-00000000000a','a0000000-0000-0000-0000-00000000000a','TA','ta869','a@x.co');
INSERT INTO users (id, tenant_id, email, first_name, last_name, password_hash, status) VALUES ('c0000000-0000-0000-0000-0000000000c1','a0000000-0000-0000-0000-00000000000a','u1@acme.co.za','U','One','x','ACTIVE');
\i platform/src/main/resources/db/migration/V869__tenant_sso_settings.sql
\i platform/src/main/resources/db/migration/V869__tenant_sso_settings.sql
-- the service's upsert, first with domains then with none
INSERT INTO tenant_sso_settings (tenant_id, enabled, provider, issuer_url, client_id, client_secret_ref, allowed_domains, enforce, updated_by, updated_at)
VALUES ('a0000000-0000-0000-0000-00000000000a', true, 'OIDC', 'https://login.example.com', 'cid', 'SSO_SECRET', string_to_array('acme.co.za,acme.com', ','), false, NULL, now())
ON CONFLICT (tenant_id) DO UPDATE SET enabled = EXCLUDED.enabled, allowed_domains = EXCLUDED.allowed_domains, enforce = EXCLUDED.enforce, updated_at = now();
INSERT INTO r SELECT 'domains stored and read back', (SELECT array_to_string(allowed_domains, ',') FROM tenant_sso_settings)='acme.co.za,acme.com';
INSERT INTO tenant_sso_settings (tenant_id, enabled, provider, issuer_url, client_id, client_secret_ref, allowed_domains, enforce, updated_by, updated_at)
VALUES ('a0000000-0000-0000-0000-00000000000a', false, 'OIDC', NULL, NULL, NULL, string_to_array('', ','), false, NULL, now())
ON CONFLICT (tenant_id) DO UPDATE SET enabled = EXCLUDED.enabled, issuer_url=EXCLUDED.issuer_url, client_id=EXCLUDED.client_id, allowed_domains = EXCLUDED.allowed_domains, enforce = EXCLUDED.enforce, updated_at = now();
INSERT INTO r SELECT 'empty domains is an empty array, read back as empty string', (SELECT cardinality(allowed_domains)=0 AND array_to_string(allowed_domains, ',')='' FROM tenant_sso_settings);
-- constraints
DO $$ BEGIN BEGIN UPDATE tenant_sso_settings SET enabled=true; INSERT INTO r VALUES ('enable without issuer/domains refused', false); EXCEPTION WHEN check_violation THEN INSERT INTO r VALUES ('enable without issuer/domains refused', true); END; END $$;
DO $$ BEGIN BEGIN UPDATE tenant_sso_settings SET enforce=true; INSERT INTO r VALUES ('enforce without enabled refused', false); EXCEPTION WHEN check_violation THEN INSERT INTO r VALUES ('enforce without enabled refused', true); END; END $$;
DO $$ BEGIN BEGIN UPDATE tenant_sso_settings SET provider='SAML'; INSERT INTO r VALUES ('provider other than OIDC refused', false); EXCEPTION WHEN check_violation THEN INSERT INTO r VALUES ('provider other than OIDC refused', true); END; END $$;
DO $$ BEGIN BEGIN UPDATE tenant_sso_settings SET allowed_domains = ARRAY(SELECT 'd'||g||'.co.za' FROM generate_series(1,21) g); INSERT INTO r VALUES ('21 domains refused', false); EXCEPTION WHEN check_violation THEN INSERT INTO r VALUES ('21 domains refused', true); END; END $$;
-- break glass
INSERT INTO tenant_sso_break_glass (tenant_id,user_id,created_by) VALUES ('a0000000-0000-0000-0000-00000000000a','c0000000-0000-0000-0000-0000000000c1',NULL);
INSERT INTO r SELECT 'user query', (SELECT count(*) FROM users WHERE id='c0000000-0000-0000-0000-0000000000c1' AND tenant_id='a0000000-0000-0000-0000-00000000000a' AND status='ACTIVE')=1;
DELETE FROM users WHERE id='c0000000-0000-0000-0000-0000000000c1';
INSERT INTO r SELECT 'break glass removed with the user', (SELECT count(*) FROM tenant_sso_break_glass)=0;
SELECT label, CASE WHEN ok THEN 'PASS' ELSE 'FAIL' END FROM r;
ROLLBACK;
