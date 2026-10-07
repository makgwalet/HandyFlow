-- V332__security_guard_score_history.sql
--
-- One row per guard per day: the operational score and the risk recommendations that stood at the nightly run.
-- It gives the Performance tab a trend, and lets the nightly job tell a recommendation that is new from one that
-- has been standing for days (only a new one sends a notification). Rows are written by the system only.
-- snapshot_date is the South African calendar day the snapshot describes.

CREATE TABLE security_guard_score_history (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    guard_id         UUID NOT NULL REFERENCES security_guards (id),
    snapshot_date    DATE NOT NULL,
    score            INTEGER,
    band             VARCHAR(30),
    coverage         INTEGER NOT NULL DEFAULT 0,
    recommendations  VARCHAR(500) NOT NULL DEFAULT '',
    created_at       TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
    CONSTRAINT uq_guard_score_history UNIQUE (tenant_id, guard_id, snapshot_date),
    CONSTRAINT ck_guard_score_history_score CHECK (score IS NULL OR score BETWEEN 0 AND 100)
);

CREATE INDEX idx_guard_score_history_guard ON security_guard_score_history (tenant_id, guard_id, snapshot_date DESC);

COMMENT ON TABLE security_guard_score_history IS
    'Daily snapshot of a guard''s operational score and risk recommendation codes ("CODE:LEVEL,CODE:LEVEL"). Written by the nightly job only.';
