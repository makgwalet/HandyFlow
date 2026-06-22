-- Test data for @DataJpaTest / @SpringBootTest
-- Only loaded in test profile (src/test/resources/db/migration/)

INSERT INTO tenants (id, tenant_id, name, slug, email, status)
VALUES (
    '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f',
    '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f',
    'Zeta Test Clinic',
    'zeta-test',
    'test@zeta.co.za',
    'ACTIVE'
) ON CONFLICT DO NOTHING;
