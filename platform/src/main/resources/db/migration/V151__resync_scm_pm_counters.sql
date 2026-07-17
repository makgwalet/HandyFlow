-- ═══════════════════════════════════════════════════════════════════════════
-- IMPORTANT: rename this file before running it.
-- "V999" is a placeholder — replace it with your project's actual next
-- sequential Flyway version number (I don't have access to your migrations
-- folder, so I can't know what that is without guessing).
-- ═══════════════════════════════════════════════════════════════════════════
--
-- One-time repair: SequenceService.next() (in the Projects module,
-- za.co.handyflow.platform.projects.application.internal) is genuinely
-- correct and race-condition-safe — the atomic
-- INSERT ... ON CONFLICT DO UPDATE ... RETURNING on pm_counters really does
-- serialize concurrent callers correctly. This is NOT a code bug.
--
-- The actual problem: pm_counters had no row (or a stale one) for SCM's
-- "PO"/"GR"/"SINV" counter types for at least one tenant, almost certainly
-- because the existing numbered rows in sc_purchase_orders/
-- sc_goods_receipts/sc_supplier_invoices were seeded directly into the
-- database rather than created through this counter-driven path. The first
-- REAL call to next() for each counter type correctly found no existing
-- row, started fresh at 1, and collided with already-seeded PO-00001 /
-- SINV-00001. Confirmed via real error logs showing duplicate-key
-- violations on exactly those two values.
--
-- Column names and number formats verified directly against the real
-- entity mappings and ScmService.java (not guessed):
--   ScPurchaseOrder.orderNumber   -> "PO-"   + %05d  (sc_purchase_orders.order_number)
--   ScGoodsReceipt.receiptNumber  -> "GR-"   + %05d  (sc_goods_receipts.receipt_number)
--   ScSupplierInvoice.invoiceNumber -> "SINV-" + %05d (sc_supplier_invoices.invoice_number)
--
-- Sets current_value to the ALREADY-USED max per tenant — not max+1 —
-- since next()'s own UPDATE increments BEFORE returning
-- (current_value = current_value + 1), so this makes the next real call
-- correctly produce max+1, not collide with max.
--
-- GREATEST(...) in the ON CONFLICT branch means this can never accidentally
-- lower an already-correct/advanced counter — only bumps it up if it's
-- behind. Safe to re-run.
--
-- The regex WHERE filters defend against any row that doesn't match the
-- expected zero-padded format (a malformed or manually-entered number,
-- for instance) — excluded from the MAX computation rather than throwing
-- and aborting the whole repair for every tenant at once.

-- Purchase Orders
INSERT INTO pm_counters (tenant_id, counter_type, current_value)
SELECT tenant_id, 'PO', MAX(CAST(SUBSTRING(order_number FROM 4) AS INT))
FROM sc_purchase_orders
WHERE order_number ~ '^PO-[0-9]{5}$'
GROUP BY tenant_id
ON CONFLICT (tenant_id, counter_type)
DO UPDATE SET current_value = GREATEST(pm_counters.current_value, EXCLUDED.current_value);

-- Goods Receipts
INSERT INTO pm_counters (tenant_id, counter_type, current_value)
SELECT tenant_id, 'GR', MAX(CAST(SUBSTRING(receipt_number FROM 4) AS INT))
FROM sc_goods_receipts
WHERE receipt_number ~ '^GR-[0-9]{5}$'
GROUP BY tenant_id
ON CONFLICT (tenant_id, counter_type)
DO UPDATE SET current_value = GREATEST(pm_counters.current_value, EXCLUDED.current_value);

-- Supplier Invoices
INSERT INTO pm_counters (tenant_id, counter_type, current_value)
SELECT tenant_id, 'SINV', MAX(CAST(SUBSTRING(invoice_number FROM 6) AS INT))
FROM sc_supplier_invoices
WHERE invoice_number ~ '^SINV-[0-9]{5}$'
GROUP BY tenant_id
ON CONFLICT (tenant_id, counter_type)
DO UPDATE SET current_value = GREATEST(pm_counters.current_value, EXCLUDED.current_value);