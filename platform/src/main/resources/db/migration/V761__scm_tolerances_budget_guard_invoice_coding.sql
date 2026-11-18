-- V761: per-tenant match tolerances, invoice cost coding (so invoices without a PO can count as budget actuals),
-- and a database guard against overlapping budgets.

-- 1. Match tolerances per tenant. No row = the application defaults (app.scm.match.*) apply.
CREATE TABLE IF NOT EXISTS sc_match_tolerances (
    tenant_id          UUID PRIMARY KEY,
    price_pct          NUMERIC(5,2)  NOT NULL,
    quantity_pct       NUMERIC(5,2)  NOT NULL,
    amount_pct         NUMERIC(5,2)  NOT NULL,
    rounding_abs       NUMERIC(10,2) NOT NULL,
    allow_header_only  BOOLEAN       NOT NULL,
    require_po         BOOLEAN       NOT NULL,
    updated_by         UUID,
    updated_by_name    VARCHAR(200),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_sc_match_tol_range CHECK (price_pct BETWEEN 0 AND 25 AND quantity_pct BETWEEN 0 AND 25
                                         AND amount_pct BETWEEN 0 AND 25 AND rounding_abs BETWEEN 0 AND 100)
);

-- 2. Invoice coding. Invoices with a PO take their department / cost centre / project from the PO commitment;
--    an invoice with no PO carries its own coding, and counts against the matching budget once approved.
ALTER TABLE sc_supplier_invoices
    ADD COLUMN IF NOT EXISTS department  VARCHAR(100),
    ADD COLUMN IF NOT EXISTS cost_centre VARCHAR(100),
    ADD COLUMN IF NOT EXISTS project_ref VARCHAR(100);
CREATE INDEX IF NOT EXISTS idx_sc_invoices_no_po_coding ON sc_supplier_invoices (tenant_id, invoice_date)
    WHERE purchase_order_id IS NULL AND status IN ('APPROVED','PAID');

-- 3. Two ACTIVE budgets for the same scope must not cover the same day. BudgetService already refuses this; this makes the
--    database refuse it too (a race between two requests, or a script). An exclusion constraint would need the btree_gist
--    extension, which not every managed Postgres allows, so this is a trigger that serialises writers per scope with an
--    advisory lock and then checks for overlap.
CREATE OR REPLACE FUNCTION sc_budget_no_overlap() RETURNS trigger AS $$
BEGIN
    IF NEW.status <> 'ACTIVE' THEN
        RETURN NEW;
    END IF;
    PERFORM pg_advisory_xact_lock(hashtextextended(NEW.tenant_id::text || '|' || NEW.scope_type || '|' || lower(NEW.scope_value), 0));
    IF EXISTS (SELECT 1 FROM sc_budgets b
                WHERE b.tenant_id = NEW.tenant_id AND b.scope_type = NEW.scope_type
                  AND lower(b.scope_value) = lower(NEW.scope_value) AND b.status = 'ACTIVE' AND b.id <> NEW.id
                  AND b.period_start <= NEW.period_end AND b.period_end >= NEW.period_start) THEN
        RAISE EXCEPTION 'An active budget for % "%" already covers part of % to %', NEW.scope_type, NEW.scope_value, NEW.period_start, NEW.period_end
            USING ERRCODE = 'unique_violation', CONSTRAINT = 'sc_budget_no_overlap';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sc_budget_no_overlap ON sc_budgets;
CREATE TRIGGER trg_sc_budget_no_overlap
    BEFORE INSERT OR UPDATE OF tenant_id, scope_type, scope_value, period_start, period_end, status ON sc_budgets
    FOR EACH ROW EXECUTE FUNCTION sc_budget_no_overlap();

-- Existing overlaps are not touched (the trigger only judges rows written from now on). List them so a person can fix them.
CREATE OR REPLACE VIEW v_sc_budget_overlaps AS
SELECT a.tenant_id, a.id AS budget_id, a.name AS budget_name, b.id AS other_budget_id, b.name AS other_name,
       a.scope_type, a.scope_value, a.period_start, a.period_end, b.period_start AS other_start, b.period_end AS other_end
  FROM sc_budgets a JOIN sc_budgets b
    ON a.tenant_id = b.tenant_id AND a.scope_type = b.scope_type AND lower(a.scope_value) = lower(b.scope_value)
   AND a.id < b.id AND a.status = 'ACTIVE' AND b.status = 'ACTIVE'
   AND a.period_start <= b.period_end AND a.period_end >= b.period_start;
