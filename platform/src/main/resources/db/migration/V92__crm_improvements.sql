-- ============================================================
-- V8__crm_improvements.sql
-- CRM module hardening & feature additions
-- ============================================================
-- WHY THIS MIGRATION EXISTS:
-- V7 had a UNIQUE constraint on (tenant_id, email) with NO partial
-- filter, meaning deleted customers block re-use of their email.
-- We drop that, add a partial unique index (only for active rows),
-- add the activity-timeline table, tags, and the customer_type
-- discriminator so we can distinguish leads from customers.
-- ============================================================

-- ── 1. Fix the email uniqueness bug ─────────────────────────────────────────
-- The old constraint blocks "re-adding" a customer who was soft-deleted.
-- A deleted row (deleted_at IS NOT NULL) should NOT block a new active row
-- with the same email.  PostgreSQL partial indexes solve this perfectly.

ALTER TABLE customers
    DROP CONSTRAINT IF EXISTS uq_customers_tenant_email;

-- New partial unique index: only enforces uniqueness among ACTIVE customers.
-- This is the correct industry pattern for soft-delete + email uniqueness.
CREATE UNIQUE INDEX uq_customers_active_email
    ON customers (tenant_id, email)
    WHERE deleted_at IS NULL;

-- ── 2. Add customer_type (lead vs customer) ──────────────────────────────────
-- Every real CRM distinguishes leads (prospects) from customers (converted).
-- We use a simple VARCHAR with a CHECK so the DB enforces valid values.
ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS customer_type VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER'
        CONSTRAINT chk_customers_type CHECK (customer_type IN ('LEAD', 'CUSTOMER'));

-- ── 3. Add status column ─────────────────────────────────────────────────────
-- Supports: ACTIVE, INACTIVE, BLOCKED.  Driven by business rules
-- (e.g. auto-mark INACTIVE after 90 days without a booking).
ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CONSTRAINT chk_customers_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'BLOCKED'));

-- ── 4. Tags (many-to-many via join table) ────────────────────────────────────
-- Tags drive segmentation (VIP, overdue, key-account, etc.).
-- We keep tags as tenant-scoped so each tenant can define their own.
CREATE TABLE IF NOT EXISTS customer_tags (
    customer_id  UUID        NOT NULL,
    tag          VARCHAR(50) NOT NULL,
    created_at   TIMESTAMP   NOT NULL DEFAULT now(),

    CONSTRAINT pk_customer_tags PRIMARY KEY (customer_id, tag),
    CONSTRAINT fk_customer_tags_customer
        FOREIGN KEY (customer_id)
        REFERENCES customers(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_customer_tags_customer ON customer_tags(customer_id);

-- ── 5. Customer activity timeline ────────────────────────────────────────────
-- Persisted audit/activity log per customer.
-- WHY NOT just log.info()?  Because log.info is write-only: you can't
-- query "show me all status changes for this customer in March."
-- This table is queryable, filterable, and exportable.
--
-- activity_type covers:
--   CREATED, UPDATED, DELETED, RESTORED,
--   NOTE_ADDED, STATUS_CHANGED, TAG_ADDED, TAG_REMOVED,
--   BOOKING_LINKED, INVOICE_LINKED   ← cross-module events via facade
CREATE TABLE IF NOT EXISTS customer_activities (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id     UUID         NOT NULL,
    customer_id   UUID         NOT NULL,
    activity_type VARCHAR(50)  NOT NULL,
    -- JSON snapshot of what changed: {"field":"email","from":"old@x.com","to":"new@x.com"}
    -- JSONB so we can index specific change types if needed later.
    payload       JSONB,
    note          TEXT,                   -- freeform human note (for NOTE_ADDED type)
    performed_by  UUID,                   -- user who triggered the activity (nullable = system)
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT pk_customer_activities PRIMARY KEY (id),
    CONSTRAINT fk_customer_activities_customer
        FOREIGN KEY (customer_id)
        REFERENCES customers(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_customer_activities_customer
    ON customer_activities(customer_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_customer_activities_tenant
    ON customer_activities(tenant_id, created_at DESC);

-- ── 6. Contacts soft-delete partial index (missed in V7) ──────────────────
-- Same pattern as customers: partial unique index for email within a customer.
CREATE UNIQUE INDEX IF NOT EXISTS uq_contacts_active_email
    ON contacts (customer_id, email)
    WHERE deleted_at IS NULL;

-- ── 7. Full-text search index on customers ───────────────────────────────────
-- The current searchActive() does ILIKE '%term%' which is a full table scan.
-- A GIN index on a tsvector makes search O(log N) instead of O(N).
-- We index: name + email + phone + tax_number.
-- WHY tsvector? Because ILIKE can't use a B-tree index on a middle wildcard.
ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS search_vector tsvector
        GENERATED ALWAYS AS (
            to_tsvector('simple',
                coalesce(name, '') || ' ' ||
                coalesce(email, '') || ' ' ||
                coalesce(phone, '') || ' ' ||
                coalesce(tax_number, '')
            )
        ) STORED;

CREATE INDEX IF NOT EXISTS idx_customers_search
    ON customers USING GIN (search_vector)
    WHERE deleted_at IS NULL;

-- ── 8. Update updated_at automatically ──────────────────────────────────────
-- V7 defined updated_at but had no trigger to keep it current.
-- Without this, updated_at is always the INSERT value.
CREATE OR REPLACE FUNCTION set_updated_at()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger
        WHERE tgname = 'trg_customers_updated_at'
    ) THEN
        CREATE TRIGGER trg_customers_updated_at
            BEFORE UPDATE ON customers
            FOR EACH ROW EXECUTE FUNCTION set_updated_at();
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger
        WHERE tgname = 'trg_contacts_updated_at'
    ) THEN
        CREATE TRIGGER trg_contacts_updated_at
            BEFORE UPDATE ON contacts
            FOR EACH ROW EXECUTE FUNCTION set_updated_at();
    END IF;
END;
$$;

-- ── Comments for future devs ──────────────────────────────────────────────────
COMMENT ON TABLE customer_activities IS
    'Immutable audit log of all customer lifecycle events. Never UPDATE or DELETE rows here.';
COMMENT ON COLUMN customers.customer_type IS
    'LEAD = prospect not yet converted. CUSTOMER = paying/active client.';
COMMENT ON COLUMN customers.status IS
    'ACTIVE=normal, INACTIVE=no activity 90+ days, BLOCKED=do not transact.';
COMMENT ON INDEX uq_customers_active_email IS
    'Partial unique index: email must be unique per tenant among non-deleted customers only. Allows re-adding a previously deleted email.';
