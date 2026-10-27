-- Reporting (dashboard, vendor performance, asset reliability, SLA portfolio) reads work orders, invoices and
-- credit notes by tenant and date range. Indexes only — no table or column changes. Idempotent.

-- Provider work orders: "created in period" and "completed in period" scans.
CREATE INDEX IF NOT EXISTS idx_fm_wo_tenant_created ON fm_work_orders (tenant_id, created_at);
CREATE INDEX IF NOT EXISTS idx_fm_wo_tenant_completed ON fm_work_orders (tenant_id, completed_at) WHERE status = 'COMPLETED';

-- Internal work orders: same two access paths.
CREATE INDEX IF NOT EXISTS idx_facility_wo_tenant_created ON facility_work_orders (tenant_id, created_at);
CREATE INDEX IF NOT EXISTS idx_facility_wo_tenant_completed ON facility_work_orders (tenant_id, completed_at) WHERE status = 'COMPLETED';

-- Finance KPIs: receivables / drafts by status, invoiced-this-month by issue date, credit notes awaiting approval.
CREATE INDEX IF NOT EXISTS idx_fm_invoices_tenant_status ON fm_invoices (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_fm_invoices_tenant_issue ON fm_invoices (tenant_id, issue_date);
CREATE INDEX IF NOT EXISTS idx_fm_credit_notes_tenant_status ON fm_credit_notes (tenant_id, status);

-- Compliance KPIs: expiring / lapsed per tenant.
CREATE INDEX IF NOT EXISTS idx_facility_cert_tenant_expiry ON facility_compliance_certificates (tenant_id, expiry_date);
