-- Owner decision of 5 November 2026 (18:07): tier limits approved. Limits are versioned so a change is a new row with
-- an effective date, never an overwrite, and the history of what applied when is kept. Version 1 is the provisional set
-- from V594. Version 2 is the approved set and adds quotes. Enterprise is negotiated, so it has no row.
-- Approving the limits does NOT switch any cap on: enforcement stays off until the owner approves a staged rollout.
ALTER TABLE usage_limits ADD COLUMN IF NOT EXISTS version        INTEGER NOT NULL DEFAULT 1;
ALTER TABLE usage_limits ADD COLUMN IF NOT EXISTS effective_from DATE    NOT NULL DEFAULT DATE '2026-11-05';
ALTER TABLE usage_limits ADD COLUMN IF NOT EXISTS approved_by    VARCHAR(100);
ALTER TABLE usage_limits ADD COLUMN IF NOT EXISTS approved_on    DATE;
ALTER TABLE usage_limits DROP CONSTRAINT IF EXISTS usage_limits_pkey;
ALTER TABLE usage_limits ADD PRIMARY KEY (tier, metric, version);

INSERT INTO usage_limits (tier, metric, version, limit_value, provisional, effective_from, approved_by, approved_on) VALUES
    ('STARTER',  'SEATS',           2,    5, FALSE, DATE '2026-11-05', 'Owner', DATE '2026-11-05'),
    ('STARTER',  'QUOTES_ISSUED',   2,  100, FALSE, DATE '2026-11-05', 'Owner', DATE '2026-11-05'),
    ('STARTER',  'INVOICES_ISSUED', 2,   50, FALSE, DATE '2026-11-05', 'Owner', DATE '2026-11-05'),
    ('GROWTH',   'SEATS',           2,   20, FALSE, DATE '2026-11-05', 'Owner', DATE '2026-11-05'),
    ('GROWTH',   'QUOTES_ISSUED',   2,  500, FALSE, DATE '2026-11-05', 'Owner', DATE '2026-11-05'),
    ('GROWTH',   'INVOICES_ISSUED', 2,  250, FALSE, DATE '2026-11-05', 'Owner', DATE '2026-11-05'),
    ('BUSINESS', 'SEATS',           2,   50, FALSE, DATE '2026-11-05', 'Owner', DATE '2026-11-05'),
    ('BUSINESS', 'QUOTES_ISSUED',   2, 2000, FALSE, DATE '2026-11-05', 'Owner', DATE '2026-11-05'),
    ('BUSINESS', 'INVOICES_ISSUED', 2, 1000, FALSE, DATE '2026-11-05', 'Owner', DATE '2026-11-05')
ON CONFLICT (tier, metric, version) DO NOTHING;

-- A quote counts the first time it leaves DRAFT (that is, when it is sent). Withdrawing, rejecting or letting it expire
-- later does not give the usage back; the usage_events table is append-only.
CREATE OR REPLACE FUNCTION usage_count_issued_quote() RETURNS trigger AS $$
BEGIN
    IF NEW.status <> 'DRAFT' AND (TG_OP = 'INSERT' OR OLD.status = 'DRAFT') THEN
        INSERT INTO usage_events (tenant_id, metric, period, source_id)
        VALUES (NEW.tenant_id, 'QUOTES_ISSUED', to_char(timezone('Africa/Johannesburg', now()), 'YYYY-MM'), NEW.id)
        ON CONFLICT (tenant_id, metric, source_id) DO NOTHING;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_quotes_count_issued ON quotes;
CREATE TRIGGER trg_quotes_count_issued AFTER INSERT OR UPDATE OF status ON quotes
    FOR EACH ROW EXECUTE FUNCTION usage_count_issued_quote();

INSERT INTO usage_events (tenant_id, metric, period, source_id, occurred_at)
SELECT tenant_id, 'QUOTES_ISSUED', to_char(timezone('Africa/Johannesburg', created_at), 'YYYY-MM'), id, created_at
  FROM quotes
 WHERE status <> 'DRAFT'
ON CONFLICT (tenant_id, metric, source_id) DO NOTHING;
