\set ON_ERROR_STOP on
BEGIN;
CREATE TEMP TABLE r(label text, ok boolean);
INSERT INTO tenants (id, tenant_id, name, slug, email) VALUES ('a0000000-0000-0000-0000-00000000000a','a0000000-0000-0000-0000-00000000000a','TA','ta868','a@x.co'),('b0000000-0000-0000-0000-00000000000b','b0000000-0000-0000-0000-00000000000b','TB','tb868','b@x.co') ON CONFLICT DO NOTHING;
INSERT INTO users (id, tenant_id, email, first_name, last_name, password_hash) VALUES ('c0000000-0000-0000-0000-0000000000c1','a0000000-0000-0000-0000-00000000000a','u868@x.co','U','One','x');
INSERT INTO security_branches (id, tenant_id, name, region) VALUES
 ('d0000000-0000-0000-0000-0000000000d1','a0000000-0000-0000-0000-00000000000a','Durban','  KZN '),
 ('d0000000-0000-0000-0000-0000000000d2','a0000000-0000-0000-0000-00000000000a','Pinetown','kzn'),
 ('d0000000-0000-0000-0000-0000000000d3','a0000000-0000-0000-0000-00000000000a','Sandton','Gauteng'),
 ('d0000000-0000-0000-0000-0000000000d4','a0000000-0000-0000-0000-00000000000a','Nowhere',NULL),
 ('d0000000-0000-0000-0000-0000000000d5','a0000000-0000-0000-0000-00000000000a','Blank','   '),
 ('d0000000-0000-0000-0000-0000000000d6','b0000000-0000-0000-0000-00000000000b','Durban','KZN');
\i platform/src/main/resources/db/migration/V868__security_regions.sql
\i platform/src/main/resources/db/migration/V868__security_regions.sql
INSERT INTO r SELECT 'two tenants-regions per tenant dedup (A: KZN+Gauteng=2)', (SELECT count(*) FROM security_regions WHERE tenant_id='a0000000-0000-0000-0000-00000000000a')=2;
INSERT INTO r SELECT 'tenant B own KZN region', (SELECT count(*) FROM security_regions WHERE tenant_id='b0000000-0000-0000-0000-00000000000b')=1;
INSERT INTO r SELECT 'both KZN branches share a region', (SELECT count(DISTINCT region_id) FROM security_branches WHERE id IN ('d0000000-0000-0000-0000-0000000000d1','d0000000-0000-0000-0000-0000000000d2'))=1;
INSERT INTO r SELECT 'null/blank stay without region', (SELECT count(*) FROM security_branches WHERE id IN ('d0000000-0000-0000-0000-0000000000d4','d0000000-0000-0000-0000-0000000000d5') AND region_id IS NULL)=2;
INSERT INTO r SELECT 'label normalised to region name', (SELECT region FROM security_branches WHERE id='d0000000-0000-0000-0000-0000000000d2')='KZN';
INSERT INTO r SELECT 'B branch points at B region', (SELECT tenant_id FROM security_regions WHERE id=(SELECT region_id FROM security_branches WHERE id='d0000000-0000-0000-0000-0000000000d6'))='b0000000-0000-0000-0000-00000000000b';
-- rename propagates
UPDATE security_regions SET name='KwaZulu-Natal' WHERE tenant_id='a0000000-0000-0000-0000-00000000000a' AND lower(name)='kzn';
INSERT INTO r SELECT 'rename updates branch labels', (SELECT count(*) FROM security_branches WHERE region='KwaZulu-Natal')=2;
INSERT INTO r SELECT 'rename leaves tenant B alone', (SELECT region FROM security_branches WHERE id='d0000000-0000-0000-0000-0000000000d6')='KZN';
-- text edit cannot desync a branch that has a region
UPDATE security_branches SET region='Hacked' WHERE id='d0000000-0000-0000-0000-0000000000d1';
INSERT INTO r SELECT 'text edit overwritten by region name', (SELECT region FROM security_branches WHERE id='d0000000-0000-0000-0000-0000000000d1')='KwaZulu-Natal';
-- unique name ignoring case per tenant, but not across tenants
SAVEPOINT s1;
DO $$ BEGIN BEGIN INSERT INTO security_regions (tenant_id,name) VALUES ('a0000000-0000-0000-0000-00000000000a','gauteng'); INSERT INTO r VALUES ('dup name refused', false); EXCEPTION WHEN unique_violation THEN INSERT INTO r VALUES ('dup name refused', true); END; END $$;
INSERT INTO security_regions (tenant_id,name) VALUES ('b0000000-0000-0000-0000-00000000000b','Gauteng');
INSERT INTO r VALUES ('same name allowed in another tenant', true);
-- cross-tenant link refused
DO $$ BEGIN BEGIN UPDATE security_branches SET region_id=(SELECT id FROM security_regions WHERE tenant_id='b0000000-0000-0000-0000-00000000000b' LIMIT 1) WHERE id='d0000000-0000-0000-0000-0000000000d4'; INSERT INTO r VALUES ('cross-tenant region refused', false); EXCEPTION WHEN foreign_key_violation THEN INSERT INTO r VALUES ('cross-tenant region refused', true); END; END $$;
DO $$ BEGIN BEGIN INSERT INTO security_user_regions (tenant_id,user_id,region_id) VALUES ('a0000000-0000-0000-0000-00000000000a','c0000000-0000-0000-0000-0000000000c1',(SELECT id FROM security_regions WHERE tenant_id='b0000000-0000-0000-0000-00000000000b' LIMIT 1)); INSERT INTO r VALUES ('cross-tenant user region refused', false); EXCEPTION WHEN foreign_key_violation THEN INSERT INTO r VALUES ('cross-tenant user region refused', true); END; END $$;
-- user holds a region: region cannot be deleted
INSERT INTO security_user_regions (tenant_id,user_id,region_id) VALUES ('a0000000-0000-0000-0000-00000000000a','c0000000-0000-0000-0000-0000000000c1',(SELECT id FROM security_regions WHERE tenant_id='a0000000-0000-0000-0000-00000000000a' AND name='Gauteng'));
DO $$ BEGIN BEGIN DELETE FROM security_regions WHERE tenant_id='a0000000-0000-0000-0000-00000000000a' AND name='Gauteng'; INSERT INTO r VALUES ('held region cannot be deleted', false); EXCEPTION WHEN foreign_key_violation THEN INSERT INTO r VALUES ('held region cannot be deleted', true); END; END $$;
-- region with branches cannot be deleted until freed
DO $$ BEGIN BEGIN DELETE FROM security_regions WHERE name='KwaZulu-Natal'; INSERT INTO r VALUES ('region with branches cannot be deleted', false); EXCEPTION WHEN foreign_key_violation THEN INSERT INTO r VALUES ('region with branches cannot be deleted', true); END; END $$;
UPDATE security_branches SET region_id=NULL WHERE region='KwaZulu-Natal';
DELETE FROM security_regions WHERE name='KwaZulu-Natal';
INSERT INTO r SELECT 'freed region deletes', (SELECT count(*) FROM security_regions WHERE name='KwaZulu-Natal')=0;
-- user delete cascades
DELETE FROM users WHERE id='c0000000-0000-0000-0000-0000000000c1';
INSERT INTO r SELECT 'user delete removes region rows', (SELECT count(*) FROM security_user_regions)=0;
-- RLS applied
INSERT INTO r SELECT 'rls on regions', (SELECT relrowsecurity FROM pg_class WHERE relname='security_regions');
INSERT INTO r SELECT 'rls on user_regions', (SELECT relrowsecurity FROM pg_class WHERE relname='security_user_regions');
SELECT label, CASE WHEN ok THEN 'PASS' ELSE 'FAIL' END FROM r;
ROLLBACK;
