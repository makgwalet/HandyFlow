-- Usage metering, REPORT MODE: counts are kept and shown, nothing is blocked.
--
-- usage_events is append-only. An invoice counts the first time it leaves DRAFT for anything other than CANCELLED,
-- so voiding, cancelling or deleting it later does not give the usage back, and credit notes (a different table)
-- never count. The trigger sits on the invoices table so every way of issuing one is counted (manual, auto-issue,
-- recurring, quote conversion, scheduler) without each path having to remember.
CREATE TABLE IF NOT EXISTS usage_events (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    UUID         NOT NULL,
    metric       VARCHAR(40)  NOT NULL,
    period       CHAR(7)      NOT NULL,                -- yyyy-mm in South African time
    source_id    UUID         NOT NULL,
    occurred_at  TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_usage_events_source UNIQUE (tenant_id, metric, source_id)
);
CREATE INDEX IF NOT EXISTS idx_usage_events_period ON usage_events (tenant_id, metric, period);

CREATE OR REPLACE FUNCTION usage_events_no_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'usage_events is append-only';
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_usage_events_no_update ON usage_events;
CREATE TRIGGER trg_usage_events_no_update BEFORE UPDATE OR DELETE ON usage_events
    FOR EACH ROW EXECUTE FUNCTION usage_events_no_change();

CREATE OR REPLACE FUNCTION usage_count_issued_invoice() RETURNS trigger AS $$
BEGIN
    IF NEW.status NOT IN ('DRAFT', 'CANCELLED')
       AND (TG_OP = 'INSERT' OR OLD.status = 'DRAFT') THEN
        INSERT INTO usage_events (tenant_id, metric, period, source_id)
        VALUES (NEW.tenant_id, 'INVOICES_ISSUED', to_char(timezone('Africa/Johannesburg', now()), 'YYYY-MM'), NEW.id)
        ON CONFLICT (tenant_id, metric, source_id) DO NOTHING;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_invoices_count_issued ON invoices;
CREATE TRIGGER trg_invoices_count_issued AFTER INSERT OR UPDATE OF status ON invoices
    FOR EACH ROW EXECUTE FUNCTION usage_count_issued_invoice();

-- Invoices that are already issued. Their month is the month they were issued. An invoice that was issued and then
-- cancelled before this migration cannot be told from a cancelled draft, so it is not counted.
INSERT INTO usage_events (tenant_id, metric, period, source_id, occurred_at)
SELECT tenant_id, 'INVOICES_ISSUED',
       to_char(timezone('Africa/Johannesburg', COALESCE(issued_at, created_at)), 'YYYY-MM'), id, COALESCE(issued_at, created_at)
  FROM invoices
 WHERE status NOT IN ('DRAFT', 'CANCELLED')
ON CONFLICT (tenant_id, metric, source_id) DO NOTHING;

-- Which tier a business is on. Null until someone assigns it: no tier is guessed, and without one no limit is shown.
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS usage_tier VARCHAR(20);
ALTER TABLE tenants DROP CONSTRAINT IF EXISTS chk_tenants_usage_tier;
ALTER TABLE tenants ADD CONSTRAINT chk_tenants_usage_tier CHECK (usage_tier IS NULL OR usage_tier IN ('STARTER', 'GROWTH', 'BUSINESS', 'ENTERPRISE'));

-- Limits per tier. PROVISIONAL until the owner gives final commercial approval (decisions of 5 November 2026).
-- Enterprise is negotiated per business, so it has no row and therefore no limit here.
CREATE TABLE IF NOT EXISTS usage_limits (
    tier         VARCHAR(20)  NOT NULL,
    metric       VARCHAR(40)  NOT NULL,
    limit_value  INTEGER      NOT NULL,
    provisional  BOOLEAN      NOT NULL DEFAULT TRUE,
    PRIMARY KEY (tier, metric)
);
INSERT INTO usage_limits (tier, metric, limit_value) VALUES
    ('STARTER',  'INVOICES_ISSUED', 50),   ('GROWTH',  'INVOICES_ISSUED', 250), ('BUSINESS', 'INVOICES_ISSUED', 1000),
    ('STARTER',  'SEATS',            5),   ('GROWTH',  'SEATS',            20),  ('BUSINESS', 'SEATS',            50)
ON CONFLICT (tier, metric) DO NOTHING;
