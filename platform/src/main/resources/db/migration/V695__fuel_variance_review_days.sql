-- Tell the administrators when a delivery variance has stayed unresolved too long. The number of days is the company's own; empty means the alert is off.
-- overdue_alerted_at makes sure each variance is mentioned once.
ALTER TABLE fuel_settings ADD COLUMN variance_review_days INTEGER;
ALTER TABLE fuel_settings ADD CONSTRAINT ck_fuel_settings_variance_review_days CHECK (variance_review_days IS NULL OR (variance_review_days >= 1 AND variance_review_days <= 365));
ALTER TABLE fuel_delivery_variances ADD COLUMN overdue_alerted_at TIMESTAMPTZ;
