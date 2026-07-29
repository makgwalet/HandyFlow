-- Rename this file to the actual next Flyway version before running.

ALTER TABLE quotes
    ADD COLUMN first_viewed_at TIMESTAMPTZ,
    ADD COLUMN last_viewed_at  TIMESTAMPTZ,
    ADD COLUMN view_count      INTEGER NOT NULL DEFAULT 0;