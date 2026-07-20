-- ═══════════════════════════════════════════════════════════════════════════
-- IMPORTANT: rename this file before running it — "V999" is a placeholder.
-- ═══════════════════════════════════════════════════════════════════════════
--
-- Adds a per-client opt-out for client-facing deadline reminder emails —
-- closes the accountant module audit's gap: "Reminder emails go only to
-- the firm... never to the client... many practices want the client cc'd
-- or separately notified so records/payment can be requested in time."
--
-- Defaults to true (on) — matches the audit's own framing that this is
-- generally desired behavior, not something a practice has to
-- explicitly turn on for every client. A per-client toggle exists
-- because not every client wants inbox noise on every SARS deadline —
-- some prefer the firm just handle it silently — this is a real
-- practice/client-relationship concern, not just a technical nicety.

ALTER TABLE acc_clients
    ADD COLUMN IF NOT EXISTS client_deadline_reminders_enabled BOOLEAN NOT NULL DEFAULT true;