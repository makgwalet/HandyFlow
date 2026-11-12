-- How early or late (in minutes) a guard may start a shift and still be linked to it. NULL = the platform default (30).
ALTER TABLE security_sites
    ADD COLUMN IF NOT EXISTS clock_in_window_minutes INTEGER
        CHECK (clock_in_window_minutes IS NULL OR clock_in_window_minutes BETWEEN 5 AND 480);
