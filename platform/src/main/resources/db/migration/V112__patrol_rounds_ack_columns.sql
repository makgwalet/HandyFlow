-- =============================================================================
-- V112__patrol_rounds_ack_columns.sql
-- Adds columns the PatrolRound entity needs that V111 omitted:
--   - off_schedule (boolean flag, separate from off_schedule_reason text)
--   - acknowledged_by / acknowledgement_note (supervisor sign-off on
--     MISSED/PARTIAL rounds)
-- =============================================================================

ALTER TABLE security_patrol_rounds
    ADD COLUMN IF NOT EXISTS off_schedule          BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS acknowledged_by        UUID,
    ADD COLUMN IF NOT EXISTS acknowledgement_note   TEXT;