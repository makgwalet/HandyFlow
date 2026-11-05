-- INV-01 check (READ ONLY): issued invoices whose revenue never reached the ledger.
-- Before patch 0647, invoices made by recurring schedules, the scheduler and quote conversion were issued without a
-- journal. This lists every issued invoice with no live journal under its posting key, newest first.
-- It changes nothing. Review the list before deciding whether to post any of them.
SELECT i.tenant_id,
       i.invoice_number,
       i.status,
       i.issued_at::date  AS issued_on,
       i.subtotal, i.vat_total, i.total,
       CASE WHEN i.quote_id IS NOT NULL THEN 'from a quote' ELSE 'other (recurring or scheduled)' END AS likely_path,
       EXISTS (SELECT 1 FROM acc_posting_failures f
                WHERE f.tenant_id = i.tenant_id AND f.source_key = 'invoicing:invoice-issued:' || i.id AND f.status = 'OPEN') AS has_open_posting_failure
  FROM invoices i
 WHERE i.status NOT IN ('DRAFT', 'CANCELLED')
   AND i.deleted_at IS NULL
   AND NOT EXISTS (SELECT 1 FROM acc_journal_entries j
                    WHERE j.tenant_id = i.tenant_id AND j.source_key = 'invoicing:invoice-issued:' || i.id AND j.deleted_at IS NULL)
 ORDER BY i.issued_at DESC NULLS LAST;
