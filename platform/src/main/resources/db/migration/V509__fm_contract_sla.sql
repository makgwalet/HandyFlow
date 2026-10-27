-- Chunk 8: Facilities Management contract coverage, rate-card pricing and SLAs.
-- RENAME to the next free Flyway version; apply AFTER the Chunk 7 migration.
--
-- WHAT CHANGES FOR EXISTING DATA
--  * Agreements default to covering ALL sites and ALL categories (what they implicitly did before).
--  * Work orders are stamped with a coverage: those raised for a client that had an active RETAINER on the
--    relevant date become INCLUDED (never billed separately — which matches what happened before, when a
--    retainer client's work orders were never invoiced); everything else becomes CHARGEABLE.
--  * Existing retainer invoices are linked to the retainer that was active at their period start.
--
-- PRE-CHECK (informational): clients with more than one ACTIVE agreement overlapping in time. New overlaps are
-- now rejected, but existing ones are kept; the most specific / most recent agreement wins when resolving.
--   SELECT tenant_id, client_id, COUNT(*) FROM fm_service_agreements
--   WHERE status = 'ACTIVE' GROUP BY tenant_id, client_id HAVING COUNT(*) > 1;

-- 1. Agreements: name, category coverage, parts markup (rate card). hourly_rate already exists.
ALTER TABLE fm_service_agreements
    ADD COLUMN IF NOT EXISTS name                 VARCHAR(100),
    ADD COLUMN IF NOT EXISTS covered_categories   VARCHAR(100),   -- comma-separated; NULL = all categories
    ADD COLUMN IF NOT EXISTS parts_markup_percent NUMERIC(6, 2);

-- Site coverage: no rows = the agreement covers every site of the client.
CREATE TABLE IF NOT EXISTS fm_agreement_sites (
    agreement_id UUID NOT NULL,
    site_id      UUID NOT NULL,
    PRIMARY KEY (agreement_id, site_id),
    CONSTRAINT fk_fm_agreement_sites_agreement FOREIGN KEY (agreement_id) REFERENCES fm_service_agreements (id) ON DELETE CASCADE
);

-- SLA targets per priority. clock: 24X7 (calendar time) or BUSINESS_HOURS (tenant business calendar).
CREATE TABLE IF NOT EXISTS fm_agreement_sla_targets (
    agreement_id       UUID        NOT NULL,
    priority           VARCHAR(10) NOT NULL,
    response_minutes   INTEGER     NOT NULL,
    resolution_minutes INTEGER     NOT NULL,
    clock              VARCHAR(20) NOT NULL,
    PRIMARY KEY (agreement_id, priority),
    CONSTRAINT fk_fm_agreement_sla_agreement FOREIGN KEY (agreement_id) REFERENCES fm_service_agreements (id) ON DELETE CASCADE,
    CONSTRAINT chk_fm_sla_minutes CHECK (response_minutes > 0 AND resolution_minutes >= response_minutes)
);

-- 2. Tenant business calendar (working hours/days/time zone) and its public holidays.
CREATE TABLE IF NOT EXISTS fm_business_calendars (
    id           UUID PRIMARY KEY,
    tenant_id    UUID         NOT NULL UNIQUE,
    start_time   TIME         NOT NULL,
    end_time     TIME         NOT NULL,
    working_days VARCHAR(30)  NOT NULL,
    time_zone    VARCHAR(50)  NOT NULL DEFAULT 'Africa/Johannesburg',
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    version      BIGINT
);
CREATE TABLE IF NOT EXISTS fm_business_calendar_holidays (
    calendar_id  UUID         NOT NULL,
    holiday_date DATE         NOT NULL,
    name         VARCHAR(100),
    PRIMARY KEY (calendar_id, holiday_date),
    CONSTRAINT fk_fm_holidays_calendar FOREIGN KEY (calendar_id) REFERENCES fm_business_calendars (id) ON DELETE CASCADE
);

-- 3. Work orders: contract terms and SLA clock, stamped at creation so later agreement changes
--    never rewrite the terms work was raised under.
ALTER TABLE fm_work_orders
    ADD COLUMN IF NOT EXISTS agreement_id             UUID,
    ADD COLUMN IF NOT EXISTS coverage                 VARCHAR(20),
    ADD COLUMN IF NOT EXISTS labour_rate              NUMERIC(15, 2),
    ADD COLUMN IF NOT EXISTS parts_markup_percent     NUMERIC(6, 2),
    ADD COLUMN IF NOT EXISTS response_target_minutes  INTEGER,
    ADD COLUMN IF NOT EXISTS resolution_target_minutes INTEGER,
    ADD COLUMN IF NOT EXISTS sla_clock                VARCHAR(20),
    ADD COLUMN IF NOT EXISTS response_due_at          TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS resolution_due_at        TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS responded_at             TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS sla_paused_at            TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS sla_paused_minutes       BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS response_warned_at       TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS response_breached_at     TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS resolution_warned_at     TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS resolution_breached_at   TIMESTAMPTZ;

UPDATE fm_work_orders w SET coverage = 'INCLUDED'
 WHERE w.coverage IS NULL
   AND EXISTS (SELECT 1 FROM fm_service_agreements a
                WHERE a.tenant_id = w.tenant_id AND a.client_id = w.client_id AND a.billing_type = 'RETAINER'
                  AND a.start_date <= COALESCE(w.completed_at::date, w.created_at::date)
                  AND (a.end_date IS NULL OR a.end_date >= COALESCE(w.completed_at::date, w.created_at::date)));
UPDATE fm_work_orders SET coverage = 'CHARGEABLE' WHERE coverage IS NULL;

-- Open work orders that have an SLA clock (used by the SLA monitor).
CREATE INDEX IF NOT EXISTS idx_fm_wo_sla_response
    ON fm_work_orders (response_due_at) WHERE response_due_at IS NOT NULL AND responded_at IS NULL AND response_breached_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_fm_wo_sla_resolution
    ON fm_work_orders (resolution_due_at) WHERE resolution_due_at IS NOT NULL AND completed_at IS NULL AND resolution_breached_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_fm_wo_agreement ON fm_work_orders (tenant_id, agreement_id);

-- 4. Invoices remember which retainer they billed, so each retainer is invoiced once per period
--    even when a client has several.
ALTER TABLE fm_invoices ADD COLUMN IF NOT EXISTS agreement_id UUID;

UPDATE fm_invoices i SET agreement_id = (
        SELECT a.id FROM fm_service_agreements a
         WHERE a.tenant_id = i.tenant_id AND a.client_id = i.client_id AND a.billing_type = 'RETAINER'
           AND a.start_date <= i.period_start AND (a.end_date IS NULL OR a.end_date >= i.period_start)
         ORDER BY a.start_date DESC LIMIT 1)
 WHERE i.agreement_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_fm_invoices_agreement ON fm_invoices (tenant_id, agreement_id, period_start, period_end);