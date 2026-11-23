\set ON_ERROR_STOP on
BEGIN;
CREATE TEMP TABLE r(label text, ok boolean);
INSERT INTO tenants (id, tenant_id, name, slug, email) VALUES ('a0000000-0000-0000-0000-00000000000a','a0000000-0000-0000-0000-00000000000a','TA','ta868','a@x.co'),('b0000000-0000-0000-0000-00000000000b','b0000000-0000-0000-0000-00000000000b','TB','tb868','b@x.co');
INSERT INTO users (id, tenant_id, email, first_name, last_name, password_hash) VALUES ('c0000000-0000-0000-0000-0000000000c1','a0000000-0000-0000-0000-00000000000a','u1@x.co','U','One','x'),('c0000000-0000-0000-0000-0000000000c2','a0000000-0000-0000-0000-00000000000a','u2@x.co','U','Two','x');
\i platform/src/main/resources/db/migration/V868__security_regions.sql
INSERT INTO security_regions (id,tenant_id,name) VALUES ('e0000000-0000-0000-0000-0000000000e1','a0000000-0000-0000-0000-00000000000a','KZN'),('e0000000-0000-0000-0000-0000000000e2','a0000000-0000-0000-0000-00000000000a','Empty');
INSERT INTO security_branches (id, tenant_id, name, region_id) VALUES
 ('d0000000-0000-0000-0000-0000000000d1','a0000000-0000-0000-0000-00000000000a','Durban','e0000000-0000-0000-0000-0000000000e1'),
 ('d0000000-0000-0000-0000-0000000000d2','a0000000-0000-0000-0000-00000000000a','Pinetown',NULL),
 ('d0000000-0000-0000-0000-0000000000d3','a0000000-0000-0000-0000-00000000000a','Sandton',NULL);
INSERT INTO security_sites (id, tenant_id, name, branch_id) VALUES
 ('f0000000-0000-0000-0000-0000000000f1','a0000000-0000-0000-0000-00000000000a','Mall','d0000000-0000-0000-0000-0000000000d1'),
 ('f0000000-0000-0000-0000-0000000000f2','a0000000-0000-0000-0000-00000000000a','Depot','d0000000-0000-0000-0000-0000000000d2'),
 ('f0000000-0000-0000-0000-0000000000f3','a0000000-0000-0000-0000-00000000000a','Tower','d0000000-0000-0000-0000-0000000000d3');
-- U holds KZN only; U2 holds the empty region only
INSERT INTO security_user_regions (tenant_id,user_id,region_id) VALUES ('a0000000-0000-0000-0000-00000000000a','c0000000-0000-0000-0000-0000000000c1','e0000000-0000-0000-0000-0000000000e1'),('a0000000-0000-0000-0000-00000000000a','c0000000-0000-0000-0000-0000000000c2','e0000000-0000-0000-0000-0000000000e2');
INSERT INTO r SELECT 'region-only user is limited', (
            SELECT (EXISTS (SELECT 1 FROM security_branch_assignments WHERE tenant_id = 'a0000000-0000-0000-0000-00000000000a' AND entity_type = 'USER' AND entity_id = 'c0000000-0000-0000-0000-0000000000c1' AND active
                            AND role IN ('MANAGER', 'VIEWER'))
                 OR EXISTS (SELECT 1 FROM security_user_scopes WHERE tenant_id = 'a0000000-0000-0000-0000-00000000000a' AND user_id = 'c0000000-0000-0000-0000-0000000000c1')
                 OR EXISTS (SELECT 1 FROM security_user_regions WHERE tenant_id = 'a0000000-0000-0000-0000-00000000000a' AND user_id = 'c0000000-0000-0000-0000-0000000000c1')));
INSERT INTO r SELECT 'sees exactly the region branch site', (SELECT array_agg(id) FROM (
            SELECT s.id FROM security_sites s
            WHERE s.tenant_id = 'a0000000-0000-0000-0000-00000000000a' AND s.deleted_at IS NULL AND (
                  s.branch_id IN (SELECT branch_id FROM security_branch_assignments
                                  WHERE tenant_id = 'a0000000-0000-0000-0000-00000000000a' AND entity_type = 'USER' AND entity_id = 'c0000000-0000-0000-0000-0000000000c1' AND active AND role IN ('MANAGER', 'VIEWER'))
               OR s.id IN (SELECT scope_id FROM security_user_scopes WHERE tenant_id = 'a0000000-0000-0000-0000-00000000000a' AND user_id = 'c0000000-0000-0000-0000-0000000000c1' AND scope_type = 'SITE')
               OR s.customer_id IN (SELECT scope_id FROM security_user_scopes WHERE tenant_id = 'a0000000-0000-0000-0000-00000000000a' AND user_id = 'c0000000-0000-0000-0000-0000000000c1' AND scope_type = 'CLIENT')
               OR s.branch_id IN (SELECT b.id FROM security_branches b WHERE b.tenant_id = 'a0000000-0000-0000-0000-00000000000a' AND b.region_id IN
                                  (SELECT r.region_id FROM security_user_regions r WHERE r.tenant_id = b.tenant_id AND r.user_id = 'c0000000-0000-0000-0000-0000000000c1')))) q) = ARRAY['f0000000-0000-0000-0000-0000000000f1'::uuid];
INSERT INTO r SELECT 'user of an empty region is limited', (
            SELECT (EXISTS (SELECT 1 FROM security_branch_assignments WHERE tenant_id = 'a0000000-0000-0000-0000-00000000000a' AND entity_type = 'USER' AND entity_id = 'c0000000-0000-0000-0000-0000000000c2' AND active
                            AND role IN ('MANAGER', 'VIEWER'))
                 OR EXISTS (SELECT 1 FROM security_user_scopes WHERE tenant_id = 'a0000000-0000-0000-0000-00000000000a' AND user_id = 'c0000000-0000-0000-0000-0000000000c2')
                 OR EXISTS (SELECT 1 FROM security_user_regions WHERE tenant_id = 'a0000000-0000-0000-0000-00000000000a' AND user_id = 'c0000000-0000-0000-0000-0000000000c2')));
INSERT INTO r SELECT 'and sees nothing', (SELECT count(*) FROM (
            SELECT s.id FROM security_sites s
            WHERE s.tenant_id = 'a0000000-0000-0000-0000-00000000000a' AND s.deleted_at IS NULL AND (
                  s.branch_id IN (SELECT branch_id FROM security_branch_assignments
                                  WHERE tenant_id = 'a0000000-0000-0000-0000-00000000000a' AND entity_type = 'USER' AND entity_id = 'c0000000-0000-0000-0000-0000000000c2' AND active AND role IN ('MANAGER', 'VIEWER'))
               OR s.id IN (SELECT scope_id FROM security_user_scopes WHERE tenant_id = 'a0000000-0000-0000-0000-00000000000a' AND user_id = 'c0000000-0000-0000-0000-0000000000c2' AND scope_type = 'SITE')
               OR s.customer_id IN (SELECT scope_id FROM security_user_scopes WHERE tenant_id = 'a0000000-0000-0000-0000-00000000000a' AND user_id = 'c0000000-0000-0000-0000-0000000000c2' AND scope_type = 'CLIENT')
               OR s.branch_id IN (SELECT b.id FROM security_branches b WHERE b.tenant_id = 'a0000000-0000-0000-0000-00000000000a' AND b.region_id IN
                                  (SELECT r.region_id FROM security_user_regions r WHERE r.tenant_id = b.tenant_id AND r.user_id = 'c0000000-0000-0000-0000-0000000000c2')))) q)=0;
-- a branch added to the region later is included at once
UPDATE security_branches SET region_id='e0000000-0000-0000-0000-0000000000e1' WHERE id='d0000000-0000-0000-0000-0000000000d2';
INSERT INTO r SELECT 'later branch included live', (SELECT count(*) FROM (
            SELECT s.id FROM security_sites s
            WHERE s.tenant_id = 'a0000000-0000-0000-0000-00000000000a' AND s.deleted_at IS NULL AND (
                  s.branch_id IN (SELECT branch_id FROM security_branch_assignments
                                  WHERE tenant_id = 'a0000000-0000-0000-0000-00000000000a' AND entity_type = 'USER' AND entity_id = 'c0000000-0000-0000-0000-0000000000c1' AND active AND role IN ('MANAGER', 'VIEWER'))
               OR s.id IN (SELECT scope_id FROM security_user_scopes WHERE tenant_id = 'a0000000-0000-0000-0000-00000000000a' AND user_id = 'c0000000-0000-0000-0000-0000000000c1' AND scope_type = 'SITE')
               OR s.customer_id IN (SELECT scope_id FROM security_user_scopes WHERE tenant_id = 'a0000000-0000-0000-0000-00000000000a' AND user_id = 'c0000000-0000-0000-0000-0000000000c1' AND scope_type = 'CLIENT')
               OR s.branch_id IN (SELECT b.id FROM security_branches b WHERE b.tenant_id = 'a0000000-0000-0000-0000-00000000000a' AND b.region_id IN
                                  (SELECT r.region_id FROM security_user_regions r WHERE r.tenant_id = b.tenant_id AND r.user_id = 'c0000000-0000-0000-0000-0000000000c1')))) q)=2;
-- taken out again
UPDATE security_branches SET region_id=NULL WHERE id='d0000000-0000-0000-0000-0000000000d2';
INSERT INTO r SELECT 'removed branch excluded live', (SELECT count(*) FROM (
            SELECT s.id FROM security_sites s
            WHERE s.tenant_id = 'a0000000-0000-0000-0000-00000000000a' AND s.deleted_at IS NULL AND (
                  s.branch_id IN (SELECT branch_id FROM security_branch_assignments
                                  WHERE tenant_id = 'a0000000-0000-0000-0000-00000000000a' AND entity_type = 'USER' AND entity_id = 'c0000000-0000-0000-0000-0000000000c1' AND active AND role IN ('MANAGER', 'VIEWER'))
               OR s.id IN (SELECT scope_id FROM security_user_scopes WHERE tenant_id = 'a0000000-0000-0000-0000-00000000000a' AND user_id = 'c0000000-0000-0000-0000-0000000000c1' AND scope_type = 'SITE')
               OR s.customer_id IN (SELECT scope_id FROM security_user_scopes WHERE tenant_id = 'a0000000-0000-0000-0000-00000000000a' AND user_id = 'c0000000-0000-0000-0000-0000000000c1' AND scope_type = 'CLIENT')
               OR s.branch_id IN (SELECT b.id FROM security_branches b WHERE b.tenant_id = 'a0000000-0000-0000-0000-00000000000a' AND b.region_id IN
                                  (SELECT r.region_id FROM security_user_regions r WHERE r.tenant_id = b.tenant_id AND r.user_id = 'c0000000-0000-0000-0000-0000000000c1')))) q)=1;
-- deleted site not counted
UPDATE security_sites SET deleted_at=now() WHERE id='f0000000-0000-0000-0000-0000000000f1';
INSERT INTO r SELECT 'deleted site not counted', (SELECT count(*) FROM (
            SELECT s.id FROM security_sites s
            WHERE s.tenant_id = 'a0000000-0000-0000-0000-00000000000a' AND s.deleted_at IS NULL AND (
                  s.branch_id IN (SELECT branch_id FROM security_branch_assignments
                                  WHERE tenant_id = 'a0000000-0000-0000-0000-00000000000a' AND entity_type = 'USER' AND entity_id = 'c0000000-0000-0000-0000-0000000000c1' AND active AND role IN ('MANAGER', 'VIEWER'))
               OR s.id IN (SELECT scope_id FROM security_user_scopes WHERE tenant_id = 'a0000000-0000-0000-0000-00000000000a' AND user_id = 'c0000000-0000-0000-0000-0000000000c1' AND scope_type = 'SITE')
               OR s.customer_id IN (SELECT scope_id FROM security_user_scopes WHERE tenant_id = 'a0000000-0000-0000-0000-00000000000a' AND user_id = 'c0000000-0000-0000-0000-0000000000c1' AND scope_type = 'CLIENT')
               OR s.branch_id IN (SELECT b.id FROM security_branches b WHERE b.tenant_id = 'a0000000-0000-0000-0000-00000000000a' AND b.region_id IN
                                  (SELECT r.region_id FROM security_user_regions r WHERE r.tenant_id = b.tenant_id AND r.user_id = 'c0000000-0000-0000-0000-0000000000c1')))) q)=0;
UPDATE security_sites SET deleted_at=NULL WHERE id='f0000000-0000-0000-0000-0000000000f1';
-- overview
INSERT INTO r SELECT 'overview lists both with regions=1 and right visible counts',
  (SELECT count(*) FROM (
            SELECT u.id, u.first_name, u.last_name, u.email, u.status,
                   (SELECT COUNT(*) FROM security_branch_assignments a WHERE a.tenant_id = u.tenant_id AND a.entity_type = 'USER' AND a.entity_id = u.id
                        AND a.active AND a.role IN ('MANAGER', 'VIEWER')) AS branches,
                   (SELECT COUNT(*) FROM security_user_regions g WHERE g.tenant_id = u.tenant_id AND g.user_id = u.id) AS regions,
                   (SELECT COUNT(*) FROM security_user_scopes x WHERE x.tenant_id = u.tenant_id AND x.user_id = u.id AND x.scope_type = 'CLIENT') AS clients,
                   (SELECT COUNT(*) FROM security_user_scopes x WHERE x.tenant_id = u.tenant_id AND x.user_id = u.id AND x.scope_type = 'SITE') AS sites,
                   (SELECT COUNT(*) FROM security_sites s WHERE s.tenant_id = u.tenant_id AND s.deleted_at IS NULL AND (
                        s.branch_id IN (SELECT a.branch_id FROM security_branch_assignments a WHERE a.tenant_id = u.tenant_id AND a.entity_type = 'USER'
                                        AND a.entity_id = u.id AND a.active AND a.role IN ('MANAGER', 'VIEWER'))
                     OR s.branch_id IN (SELECT b.id FROM security_branches b WHERE b.tenant_id = u.tenant_id AND b.region_id IN
                                        (SELECT g.region_id FROM security_user_regions g WHERE g.tenant_id = u.tenant_id AND g.user_id = u.id))
                     OR s.id IN (SELECT x.scope_id FROM security_user_scopes x WHERE x.tenant_id = u.tenant_id AND x.user_id = u.id AND x.scope_type = 'SITE')
                     OR s.customer_id IN (SELECT x.scope_id FROM security_user_scopes x WHERE x.tenant_id = u.tenant_id AND x.user_id = u.id AND x.scope_type = 'CLIENT'))) AS visible_sites
            FROM users u
            WHERE u.tenant_id = 'a0000000-0000-0000-0000-00000000000a' AND (
                  EXISTS (SELECT 1 FROM security_branch_assignments a WHERE a.tenant_id = u.tenant_id AND a.entity_type = 'USER' AND a.entity_id = u.id
                          AND a.active AND a.role IN ('MANAGER', 'VIEWER'))
               OR EXISTS (SELECT 1 FROM security_user_scopes x WHERE x.tenant_id = u.tenant_id AND x.user_id = u.id)
               OR EXISTS (SELECT 1 FROM security_user_regions g WHERE g.tenant_id = u.tenant_id AND g.user_id = u.id))
            ORDER BY u.last_name, u.first_name, u.id) q)=2 AND (SELECT regions FROM (
            SELECT u.id, u.first_name, u.last_name, u.email, u.status,
                   (SELECT COUNT(*) FROM security_branch_assignments a WHERE a.tenant_id = u.tenant_id AND a.entity_type = 'USER' AND a.entity_id = u.id
                        AND a.active AND a.role IN ('MANAGER', 'VIEWER')) AS branches,
                   (SELECT COUNT(*) FROM security_user_regions g WHERE g.tenant_id = u.tenant_id AND g.user_id = u.id) AS regions,
                   (SELECT COUNT(*) FROM security_user_scopes x WHERE x.tenant_id = u.tenant_id AND x.user_id = u.id AND x.scope_type = 'CLIENT') AS clients,
                   (SELECT COUNT(*) FROM security_user_scopes x WHERE x.tenant_id = u.tenant_id AND x.user_id = u.id AND x.scope_type = 'SITE') AS sites,
                   (SELECT COUNT(*) FROM security_sites s WHERE s.tenant_id = u.tenant_id AND s.deleted_at IS NULL AND (
                        s.branch_id IN (SELECT a.branch_id FROM security_branch_assignments a WHERE a.tenant_id = u.tenant_id AND a.entity_type = 'USER'
                                        AND a.entity_id = u.id AND a.active AND a.role IN ('MANAGER', 'VIEWER'))
                     OR s.branch_id IN (SELECT b.id FROM security_branches b WHERE b.tenant_id = u.tenant_id AND b.region_id IN
                                        (SELECT g.region_id FROM security_user_regions g WHERE g.tenant_id = u.tenant_id AND g.user_id = u.id))
                     OR s.id IN (SELECT x.scope_id FROM security_user_scopes x WHERE x.tenant_id = u.tenant_id AND x.user_id = u.id AND x.scope_type = 'SITE')
                     OR s.customer_id IN (SELECT x.scope_id FROM security_user_scopes x WHERE x.tenant_id = u.tenant_id AND x.user_id = u.id AND x.scope_type = 'CLIENT'))) AS visible_sites
            FROM users u
            WHERE u.tenant_id = 'a0000000-0000-0000-0000-00000000000a' AND (
                  EXISTS (SELECT 1 FROM security_branch_assignments a WHERE a.tenant_id = u.tenant_id AND a.entity_type = 'USER' AND a.entity_id = u.id
                          AND a.active AND a.role IN ('MANAGER', 'VIEWER'))
               OR EXISTS (SELECT 1 FROM security_user_scopes x WHERE x.tenant_id = u.tenant_id AND x.user_id = u.id)
               OR EXISTS (SELECT 1 FROM security_user_regions g WHERE g.tenant_id = u.tenant_id AND g.user_id = u.id))
            ORDER BY u.last_name, u.first_name, u.id) q WHERE id='c0000000-0000-0000-0000-0000000000c1')=1 AND (SELECT visible_sites FROM (
            SELECT u.id, u.first_name, u.last_name, u.email, u.status,
                   (SELECT COUNT(*) FROM security_branch_assignments a WHERE a.tenant_id = u.tenant_id AND a.entity_type = 'USER' AND a.entity_id = u.id
                        AND a.active AND a.role IN ('MANAGER', 'VIEWER')) AS branches,
                   (SELECT COUNT(*) FROM security_user_regions g WHERE g.tenant_id = u.tenant_id AND g.user_id = u.id) AS regions,
                   (SELECT COUNT(*) FROM security_user_scopes x WHERE x.tenant_id = u.tenant_id AND x.user_id = u.id AND x.scope_type = 'CLIENT') AS clients,
                   (SELECT COUNT(*) FROM security_user_scopes x WHERE x.tenant_id = u.tenant_id AND x.user_id = u.id AND x.scope_type = 'SITE') AS sites,
                   (SELECT COUNT(*) FROM security_sites s WHERE s.tenant_id = u.tenant_id AND s.deleted_at IS NULL AND (
                        s.branch_id IN (SELECT a.branch_id FROM security_branch_assignments a WHERE a.tenant_id = u.tenant_id AND a.entity_type = 'USER'
                                        AND a.entity_id = u.id AND a.active AND a.role IN ('MANAGER', 'VIEWER'))
                     OR s.branch_id IN (SELECT b.id FROM security_branches b WHERE b.tenant_id = u.tenant_id AND b.region_id IN
                                        (SELECT g.region_id FROM security_user_regions g WHERE g.tenant_id = u.tenant_id AND g.user_id = u.id))
                     OR s.id IN (SELECT x.scope_id FROM security_user_scopes x WHERE x.tenant_id = u.tenant_id AND x.user_id = u.id AND x.scope_type = 'SITE')
                     OR s.customer_id IN (SELECT x.scope_id FROM security_user_scopes x WHERE x.tenant_id = u.tenant_id AND x.user_id = u.id AND x.scope_type = 'CLIENT'))) AS visible_sites
            FROM users u
            WHERE u.tenant_id = 'a0000000-0000-0000-0000-00000000000a' AND (
                  EXISTS (SELECT 1 FROM security_branch_assignments a WHERE a.tenant_id = u.tenant_id AND a.entity_type = 'USER' AND a.entity_id = u.id
                          AND a.active AND a.role IN ('MANAGER', 'VIEWER'))
               OR EXISTS (SELECT 1 FROM security_user_scopes x WHERE x.tenant_id = u.tenant_id AND x.user_id = u.id)
               OR EXISTS (SELECT 1 FROM security_user_regions g WHERE g.tenant_id = u.tenant_id AND g.user_id = u.id))
            ORDER BY u.last_name, u.first_name, u.id) q WHERE id='c0000000-0000-0000-0000-0000000000c1')=1 AND (SELECT visible_sites FROM (
            SELECT u.id, u.first_name, u.last_name, u.email, u.status,
                   (SELECT COUNT(*) FROM security_branch_assignments a WHERE a.tenant_id = u.tenant_id AND a.entity_type = 'USER' AND a.entity_id = u.id
                        AND a.active AND a.role IN ('MANAGER', 'VIEWER')) AS branches,
                   (SELECT COUNT(*) FROM security_user_regions g WHERE g.tenant_id = u.tenant_id AND g.user_id = u.id) AS regions,
                   (SELECT COUNT(*) FROM security_user_scopes x WHERE x.tenant_id = u.tenant_id AND x.user_id = u.id AND x.scope_type = 'CLIENT') AS clients,
                   (SELECT COUNT(*) FROM security_user_scopes x WHERE x.tenant_id = u.tenant_id AND x.user_id = u.id AND x.scope_type = 'SITE') AS sites,
                   (SELECT COUNT(*) FROM security_sites s WHERE s.tenant_id = u.tenant_id AND s.deleted_at IS NULL AND (
                        s.branch_id IN (SELECT a.branch_id FROM security_branch_assignments a WHERE a.tenant_id = u.tenant_id AND a.entity_type = 'USER'
                                        AND a.entity_id = u.id AND a.active AND a.role IN ('MANAGER', 'VIEWER'))
                     OR s.branch_id IN (SELECT b.id FROM security_branches b WHERE b.tenant_id = u.tenant_id AND b.region_id IN
                                        (SELECT g.region_id FROM security_user_regions g WHERE g.tenant_id = u.tenant_id AND g.user_id = u.id))
                     OR s.id IN (SELECT x.scope_id FROM security_user_scopes x WHERE x.tenant_id = u.tenant_id AND x.user_id = u.id AND x.scope_type = 'SITE')
                     OR s.customer_id IN (SELECT x.scope_id FROM security_user_scopes x WHERE x.tenant_id = u.tenant_id AND x.user_id = u.id AND x.scope_type = 'CLIENT'))) AS visible_sites
            FROM users u
            WHERE u.tenant_id = 'a0000000-0000-0000-0000-00000000000a' AND (
                  EXISTS (SELECT 1 FROM security_branch_assignments a WHERE a.tenant_id = u.tenant_id AND a.entity_type = 'USER' AND a.entity_id = u.id
                          AND a.active AND a.role IN ('MANAGER', 'VIEWER'))
               OR EXISTS (SELECT 1 FROM security_user_scopes x WHERE x.tenant_id = u.tenant_id AND x.user_id = u.id)
               OR EXISTS (SELECT 1 FROM security_user_regions g WHERE g.tenant_id = u.tenant_id AND g.user_id = u.id))
            ORDER BY u.last_name, u.first_name, u.id) q WHERE id='c0000000-0000-0000-0000-0000000000c2')=0;
-- user with no scope at all is not listed
INSERT INTO users (id, tenant_id, email, first_name, last_name, password_hash) VALUES ('c0000000-0000-0000-0000-0000000000c3','a0000000-0000-0000-0000-00000000000a','u3@x.co','U','Three','x');
INSERT INTO r SELECT 'unscoped user not listed', (SELECT count(*) FROM (
            SELECT u.id, u.first_name, u.last_name, u.email, u.status,
                   (SELECT COUNT(*) FROM security_branch_assignments a WHERE a.tenant_id = u.tenant_id AND a.entity_type = 'USER' AND a.entity_id = u.id
                        AND a.active AND a.role IN ('MANAGER', 'VIEWER')) AS branches,
                   (SELECT COUNT(*) FROM security_user_regions g WHERE g.tenant_id = u.tenant_id AND g.user_id = u.id) AS regions,
                   (SELECT COUNT(*) FROM security_user_scopes x WHERE x.tenant_id = u.tenant_id AND x.user_id = u.id AND x.scope_type = 'CLIENT') AS clients,
                   (SELECT COUNT(*) FROM security_user_scopes x WHERE x.tenant_id = u.tenant_id AND x.user_id = u.id AND x.scope_type = 'SITE') AS sites,
                   (SELECT COUNT(*) FROM security_sites s WHERE s.tenant_id = u.tenant_id AND s.deleted_at IS NULL AND (
                        s.branch_id IN (SELECT a.branch_id FROM security_branch_assignments a WHERE a.tenant_id = u.tenant_id AND a.entity_type = 'USER'
                                        AND a.entity_id = u.id AND a.active AND a.role IN ('MANAGER', 'VIEWER'))
                     OR s.branch_id IN (SELECT b.id FROM security_branches b WHERE b.tenant_id = u.tenant_id AND b.region_id IN
                                        (SELECT g.region_id FROM security_user_regions g WHERE g.tenant_id = u.tenant_id AND g.user_id = u.id))
                     OR s.id IN (SELECT x.scope_id FROM security_user_scopes x WHERE x.tenant_id = u.tenant_id AND x.user_id = u.id AND x.scope_type = 'SITE')
                     OR s.customer_id IN (SELECT x.scope_id FROM security_user_scopes x WHERE x.tenant_id = u.tenant_id AND x.user_id = u.id AND x.scope_type = 'CLIENT'))) AS visible_sites
            FROM users u
            WHERE u.tenant_id = 'a0000000-0000-0000-0000-00000000000a' AND (
                  EXISTS (SELECT 1 FROM security_branch_assignments a WHERE a.tenant_id = u.tenant_id AND a.entity_type = 'USER' AND a.entity_id = u.id
                          AND a.active AND a.role IN ('MANAGER', 'VIEWER'))
               OR EXISTS (SELECT 1 FROM security_user_scopes x WHERE x.tenant_id = u.tenant_id AND x.user_id = u.id)
               OR EXISTS (SELECT 1 FROM security_user_regions g WHERE g.tenant_id = u.tenant_id AND g.user_id = u.id))
            ORDER BY u.last_name, u.first_name, u.id) q WHERE id='c0000000-0000-0000-0000-0000000000c3')=0;
-- region from another tenant cannot give access
INSERT INTO r SELECT 'tenant B sees none of tenant A', (SELECT count(*) FROM (
            SELECT s.id FROM security_sites s
            WHERE s.tenant_id = 'b0000000-0000-0000-0000-00000000000b' AND s.deleted_at IS NULL AND (
                  s.branch_id IN (SELECT branch_id FROM security_branch_assignments
                                  WHERE tenant_id = 'a0000000-0000-0000-0000-00000000000a' AND entity_type = 'USER' AND entity_id = 'c0000000-0000-0000-0000-0000000000c1' AND active AND role IN ('MANAGER', 'VIEWER'))
               OR s.id IN (SELECT scope_id FROM security_user_scopes WHERE tenant_id = 'a0000000-0000-0000-0000-00000000000a' AND user_id = 'c0000000-0000-0000-0000-0000000000c1' AND scope_type = 'SITE')
               OR s.customer_id IN (SELECT scope_id FROM security_user_scopes WHERE tenant_id = 'a0000000-0000-0000-0000-00000000000a' AND user_id = 'c0000000-0000-0000-0000-0000000000c1' AND scope_type = 'CLIENT')
               OR s.branch_id IN (SELECT b.id FROM security_branches b WHERE b.tenant_id = 'a0000000-0000-0000-0000-00000000000a' AND b.region_id IN
                                  (SELECT r.region_id FROM security_user_regions r WHERE r.tenant_id = b.tenant_id AND r.user_id = 'c0000000-0000-0000-0000-0000000000c1')))) q)=0;
SELECT label, CASE WHEN ok THEN 'PASS' ELSE 'FAIL' END FROM r;
ROLLBACK;
