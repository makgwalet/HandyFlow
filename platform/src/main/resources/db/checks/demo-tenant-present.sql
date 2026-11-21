-- Read-only. Run against a production database to see whether the demo tenant came in through the old
-- seed migrations. Nothing here changes data. Removing the demo tenant is a manual, reviewed step.
SELECT id, slug, name, created_at
FROM tenants
WHERE id IN ('9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f',
             'f3ca02b3-eca5-4035-8756-941c72ab6512',
             'aeb2b97f-9523-4bd7-a060-685a10c24831')
   OR slug = 'zeta-earthmoving';

SELECT version, description, installed_on, success
FROM flyway_schema_history
WHERE version IN ('105','105.1','106','107','108','119','120')
ORDER BY installed_rank;
