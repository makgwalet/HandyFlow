-- A company's own internal audit rules: where the risk score bands end and when overdue items are reminded and escalated.
-- No row means the standard rules (10 / 15 / 20, escalate at 14 days, remind every 7 days, first review reminder on day 3).
CREATE TABLE audit_settings (
    tenant_id                  UUID        PRIMARY KEY,
    low_max                    INTEGER     NOT NULL,
    medium_max                 INTEGER     NOT NULL,
    high_max                   INTEGER     NOT NULL,
    escalate_after_days        INTEGER     NOT NULL,
    repeat_every_days          INTEGER     NOT NULL,
    first_review_reminder_day  INTEGER     NOT NULL,
    updated_by                 UUID,
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT audit_settings_bands CHECK (low_max >= 5 AND low_max < medium_max AND medium_max < high_max AND high_max <= 24),
    CONSTRAINT audit_settings_days  CHECK (repeat_every_days BETWEEN 1 AND 30 AND escalate_after_days BETWEEN 2 AND 90 AND first_review_reminder_day BETWEEN 1 AND 30)
);
