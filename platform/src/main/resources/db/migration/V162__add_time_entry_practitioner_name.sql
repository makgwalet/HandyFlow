-- ═══════════════════════════════════════════════════════════════════════════
-- IMPORTANT: rename this file before running it — "V999" is a placeholder.
-- ═══════════════════════════════════════════════════════════════════════════
--
-- Closes the accountant module audit's "staff-level time report" gap.
--
-- Found while scoping this: acc_time_entries.practitioner_id has
-- almost certainly been NULL for every entry ever logged through the
-- existing UI — TimeTab.tsx's log-time form never had a practitioner
-- field at all, and the backend was trusting a client-supplied value
-- that the frontend never actually sent. Fixed at the source
-- (AccountantService.logTime() now derives practitioner identity from
-- the authenticated session, not the request body) rather than papered
-- over here — this migration only adds the column needed to actually
-- display who did the work, matching the same "store the name at the
-- point of action" pattern already used for uploaded_by_name /
-- recorded_by_name elsewhere in this module.
--
-- No backfill for existing rows — there is no way to retroactively
-- know who logged historical entries with a NULL practitioner_id; a
-- fabricated backfill value would be actively misleading, not neutral.

ALTER TABLE acc_time_entries
    ADD COLUMN IF NOT EXISTS practitioner_name VARCHAR(255);