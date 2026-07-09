-- V___desk_sla_pause_tracking.sql
-- (rename to the next available Flyway version number in your sequence)
--
-- Backs the SLA-pause fix. V36's own comment says "SLA clock pauses on
-- WAITING_ON_CUSTOMER" — the timestamps existed to support that design,
-- but nothing anywhere ever actually implemented the pause. dueAt was a
-- fixed deadline set once at ticket creation, never adjusted, meaning a
-- ticket waiting days on a slow customer would show as SLA-breached
-- through no fault of the support team at all.

ALTER TABLE desk_tickets
    ADD COLUMN IF NOT EXISTS paused_at TIMESTAMP;