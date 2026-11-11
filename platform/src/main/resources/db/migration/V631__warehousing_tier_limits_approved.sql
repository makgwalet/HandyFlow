-- Warehousing subscription tiers: the product owner approved the limits (11 Nov), with Enterprise webhooks unlimited. Enforcement REPORT is renamed MONITOR
-- (usage measured and warned on, nothing refused). Only tier definitions and tenant subscription settings change here; no warehouse data is touched.
UPDATE whse_subscription_tiers SET max_warehouses = 1,  max_client_accounts = 3,   max_monthly_transactions = 500,   max_webhooks = 0,  proposed = FALSE WHERE code = 'STARTER';
UPDATE whse_subscription_tiers SET max_warehouses = 2,  max_client_accounts = 10,  max_monthly_transactions = 3000,  max_webhooks = 1,  proposed = FALSE WHERE code = 'SME';
UPDATE whse_subscription_tiers SET max_warehouses = 5,  max_client_accounts = 40,  max_monthly_transactions = 15000, max_webhooks = 5,  proposed = FALSE WHERE code = 'PROFESSIONAL';
UPDATE whse_subscription_tiers SET max_warehouses = 15, max_client_accounts = 150, max_monthly_transactions = 60000, max_webhooks = 10, proposed = FALSE WHERE code = 'BUSINESS';
UPDATE whse_subscription_tiers SET max_warehouses = NULL, max_client_accounts = NULL, max_monthly_transactions = NULL, max_webhooks = NULL, proposed = FALSE WHERE code = 'ENTERPRISE';

ALTER TABLE whse_tenant_subscription DROP CONSTRAINT IF EXISTS whse_tenant_subscription_enforcement_check;
UPDATE whse_tenant_subscription SET enforcement = 'MONITOR' WHERE enforcement = 'REPORT';
ALTER TABLE whse_tenant_subscription ADD CONSTRAINT whse_tenant_subscription_enforcement_check CHECK (enforcement IN ('OFF','MONITOR','ENFORCE'));
