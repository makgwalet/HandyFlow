-- One EMP201 per tax month. A return now covers every pay run of the month that has not been filed; the runs are listed in hr_emp201_runs.
-- hr_emp201.pay_run_id stays as the first run of the return (older code and the PDF read it).
ALTER TABLE hr_emp201 ADD COLUMN IF NOT EXISTS tax_month DATE;
UPDATE hr_emp201 SET tax_month = date_trunc('month', period_end)::date WHERE tax_month IS NULL;

CREATE TABLE IF NOT EXISTS hr_emp201_runs (
    pay_run_id UUID PRIMARY KEY REFERENCES hr_pay_runs(id),
    emp201_id  UUID NOT NULL REFERENCES hr_emp201(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_hr_emp201_runs_emp201 ON hr_emp201_runs (emp201_id);

-- Draft returns of the same month are folded into the oldest one: their runs are linked to it, their totals added, the others removed.
-- Returns already filed or paid are never touched.
WITH grp AS (
    SELECT tenant_id, tax_month, (array_agg(id ORDER BY created_at, id))[1] AS keep_id, COUNT(*) AS n,
           SUM(total_paye) AS paye, SUM(total_uif) AS uif, SUM(total_sdl) AS sdl, SUM(total_eti) AS eti,
           MIN(period_start) AS pstart, MAX(period_end) AS pend
      FROM hr_emp201 WHERE status = 'DRAFT' GROUP BY tenant_id, tax_month HAVING COUNT(*) > 1
)
INSERT INTO hr_emp201_runs (pay_run_id, emp201_id)
SELECT e.pay_run_id, g.keep_id FROM hr_emp201 e
  JOIN grp g ON g.tenant_id = e.tenant_id AND g.tax_month = e.tax_month
 WHERE e.status = 'DRAFT' AND e.pay_run_id IS NOT NULL
ON CONFLICT (pay_run_id) DO NOTHING;

WITH grp AS (
    SELECT tenant_id, tax_month, (array_agg(id ORDER BY created_at, id))[1] AS keep_id,
           SUM(total_paye) AS paye, SUM(total_uif) AS uif, SUM(total_sdl) AS sdl, SUM(total_eti) AS eti, SUM(eti_carried) AS carried,
           MIN(period_start) AS pstart, MAX(period_end) AS pend
      FROM hr_emp201 WHERE status = 'DRAFT' GROUP BY tenant_id, tax_month HAVING COUNT(*) > 1
)
UPDATE hr_emp201 e SET total_paye = g.paye, total_uif = g.uif, total_sdl = g.sdl, total_eti = g.eti, eti_carried = g.carried,
       total_payable = g.paye - g.eti + g.uif + g.sdl, period_start = g.pstart, period_end = g.pend, updated_at = NOW()
  FROM grp g WHERE e.id = g.keep_id;

DELETE FROM hr_emp201 e USING (
    SELECT tenant_id, tax_month, (array_agg(id ORDER BY created_at, id))[1] AS keep_id
      FROM hr_emp201 WHERE status = 'DRAFT' GROUP BY tenant_id, tax_month HAVING COUNT(*) > 1
) g
 WHERE e.tenant_id = g.tenant_id AND e.tax_month = g.tax_month AND e.status = 'DRAFT' AND e.id <> g.keep_id;

-- Every remaining return keeps its own run.
INSERT INTO hr_emp201_runs (pay_run_id, emp201_id)
SELECT pay_run_id, id FROM hr_emp201 WHERE pay_run_id IS NOT NULL
ON CONFLICT (pay_run_id) DO NOTHING;
