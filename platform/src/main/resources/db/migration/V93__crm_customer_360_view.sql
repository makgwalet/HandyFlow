-- =============================================================================
-- V93__crm_customer_360_view.sql  (FIXED — column names verified against live schema)
-- Customer 360 view — linked booking/invoice counts per customer
-- =============================================================================
-- Schema verified 2026-06-26 via:
--   docker exec handyflow-db psql -U handyflow -d handyflow -c
--   "SELECT table_name, column_name FROM information_schema.columns
--    WHERE table_name IN ('bookings','invoices') ORDER BY table_name, ordinal_position"
--
-- BOOKINGS real column names:
--   - Soft-delete:  cancelled_at (TIMESTAMP, NULL = active booking)
--                   No deleted_at column exists on bookings.
--   - Date:         booking_date (DATE) + start_time (TIME) — separate columns.
--                   We cast them together for the "last booking" timestamp.
--   - Amount:       price (NUMERIC)
--   - Status:       status (VARCHAR) — cancelled bookings also have cancelled_at set
--
-- INVOICES real column names:
--   - Soft-delete:  deleted_at (TIMESTAMP, NULL = active) ✓ exists
--   - Amount:       total (NUMERIC) — NOT total_amount
--   - Status:       status (VARCHAR): DRAFT, SENT, PAID, OVERDUE, CANCELLED, etc.
-- =============================================================================

CREATE OR REPLACE VIEW customer_360_summary AS
WITH

-- ── active_bookings ───────────────────────────────────────────────────────────
-- Active = not cancelled (cancelled_at IS NULL) AND status is not CANCELLED.
-- We use booking_date + start_time cast to a timestamp for "last booking at".
-- booking_date is DATE, start_time is TIME — combining gives us a TIMESTAMP.
active_bookings AS (
    SELECT
        id,
        customer_id,
        tenant_id,
        (booking_date + start_time)::timestamp AS booked_at
    FROM bookings
    WHERE cancelled_at IS NULL
      AND status NOT IN ('CANCELLED', 'NO_SHOW')
),

-- ── active_invoices ───────────────────────────────────────────────────────────
-- Active = not soft-deleted (deleted_at IS NULL).
-- Amount column is `total`, not `total_amount`.
-- Outstanding = SENT or OVERDUE status.
active_invoices AS (
    SELECT
        id,
        customer_id,
        tenant_id,
        total,       -- the real column name in your invoices table
        status
    FROM invoices
    WHERE deleted_at IS NULL
)

-- ── main view ─────────────────────────────────────────────────────────────────
SELECT
    c.id                                                        AS customer_id,
    c.tenant_id,
    c.name,
    c.email,
    c.customer_type,
    c.status,

    -- Booking aggregates
    COUNT(DISTINCT b.id)                                        AS total_bookings,
    COUNT(DISTINCT b.id) FILTER (
        WHERE b.booked_at >= now() - interval '90 days'
    )                                                           AS bookings_last_90_days,
    MAX(b.booked_at)                                            AS last_booking_at,

    -- Invoice aggregates
    COUNT(DISTINCT inv.id)                                      AS total_invoices,
    COALESCE(SUM(inv.total), 0)                                 AS total_invoiced_amount,
    COUNT(DISTINCT inv.id) FILTER (
        WHERE inv.status = 'OVERDUE'
    )                                                           AS overdue_invoices,
    COALESCE(SUM(inv.total) FILTER (
        WHERE inv.status IN ('SENT', 'OVERDUE')
    ), 0)                                                       AS outstanding_amount

FROM customers c

LEFT JOIN active_bookings b
       ON b.customer_id = c.id
      AND b.tenant_id   = c.tenant_id

LEFT JOIN active_invoices inv
       ON inv.customer_id = c.id
      AND inv.tenant_id   = c.tenant_id

WHERE c.deleted_at IS NULL

GROUP BY c.id, c.tenant_id, c.name, c.email, c.customer_type, c.status;

COMMENT ON VIEW customer_360_summary IS
    'Cross-module read-only view. Aggregates booking and invoice counts per active customer. '
    'Booking active = cancelled_at IS NULL AND status NOT IN (CANCELLED, NO_SHOW). '
    'Invoice active = deleted_at IS NULL. Amount column = total (not total_amount). '
    'Never write to this view directly.';