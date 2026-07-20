-- ═══════════════════════════════════════════════════════════════════════════
-- IMPORTANT: rename this file before running it — "V999" is a placeholder.
-- ═══════════════════════════════════════════════════════════════════════════
--
-- Adds TCS PIN expiry reminder tracking to acc_clients — closes the
-- accountant module audit's "quick win" gap: "TCS PIN / FICA expiry
-- scheduled reminders — same pattern as the existing 30/7/1-day deadline
-- reminders, just pointed at tcsPinExpiry." tcsPinExpiry was already
-- captured and shown in the UI (ClientsTab) but nothing in the scheduler
-- ever watched it — confirmed via AccClientRepository.findWithExpiredTcsPin()
-- existing but never being called anywhere.
--
-- Three boolean flags, not just an exact-date match against
-- tcs_pin_expiry: matches the exact belt-and-braces pattern already used
-- by acc_tax_deadlines (reminder_30_sent/reminder_7_sent/reminder_1_sent)
-- — an exact-date match alone is idempotent against the scheduler firing
-- twice on the same day, but NOT against the scheduler missing a day
-- entirely (e.g. server down) and never getting a second chance to send
-- that reminder once the date has passed. The flags plus the date match
-- together give the same double protection the existing deadline
-- reminders already rely on.
--
-- Not touching FICA: AccClient has ficaCompleted (boolean) and
-- ficaCompletedDate (when it WAS completed) but no expiry/renewal date
-- field at all — there's nothing to build a "FICA expiry reminder"
-- against without inventing a field that doesn't exist in this schema.

ALTER TABLE acc_clients
    ADD COLUMN IF NOT EXISTS tcs_pin_reminder_30_sent BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS tcs_pin_reminder_7_sent  BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS tcs_pin_reminder_1_sent  BOOLEAN NOT NULL DEFAULT false;