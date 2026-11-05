-- READ-ONLY queries. They change nothing. Run them against the HandyFlow database and send the output back as text.
-- Nothing is activated or mapped until the real results have been reviewed.

-- ── A. The five catalogue queries (decision 8: Projects, Auditor, Accountant, AP stay report-only until these are reviewed)
SELECT key, name, monthly_price, active FROM module_catalogue ORDER BY key;
SELECT name, price_cents, max_users, included_module_count FROM plans ORDER BY sort_order;
SELECT module_key, status, count(*) FROM tenant_modules GROUP BY 1,2 ORDER BY 1,2;
SELECT status, count(*) FROM subscriptions GROUP BY 1;
SELECT count(*) AS tenants_with_module_but_no_catalogue_row
  FROM tenant_modules tm LEFT JOIN module_catalogue c ON c.key = tm.module_key WHERE c.key IS NULL;

-- ── B. Entitlement records for the four modules that stay report-only (shows what exists; invents nothing)
SELECT c.key, c.name, c.active, c.monthly_price,
       count(tm.id) FILTER (WHERE tm.status IN ('TRIAL', 'ACTIVE')) AS tenants_holding
  FROM module_catalogue c LEFT JOIN tenant_modules tm ON tm.module_key = c.key
 WHERE c.key IN ('projects', 'auditor', 'accountant', 'ap')
 GROUP BY c.key, c.name, c.active, c.monthly_price ORDER BY c.key;
SELECT tm.module_key, tm.status, count(*) AS tenants
  FROM tenant_modules tm
 WHERE tm.module_key IN ('projects', 'auditor', 'accountant', 'ap')
 GROUP BY 1, 2 ORDER BY 1, 2;

-- ── C. D-23: who holds the Professional free-specialist-module credit today, and until when (existing commitments are honoured)
SELECT t.id AS tenant_id, p.name AS plan, p.included_module_count, s.status AS subscription_status,
       s.current_period_end, count(tm.id) FILTER (WHERE tm.status IN ('TRIAL', 'ACTIVE')) AS modules_now
  FROM subscriptions s
  JOIN plans p ON p.id = s.plan_id
  JOIN tenants t ON t.id = s.tenant_id
  LEFT JOIN tenant_modules tm ON tm.tenant_id = t.id
 WHERE p.included_module_count > 0 AND p.name ILIKE '%professional%'
 GROUP BY t.id, p.name, p.included_module_count, s.status, s.current_period_end
 ORDER BY s.current_period_end;
