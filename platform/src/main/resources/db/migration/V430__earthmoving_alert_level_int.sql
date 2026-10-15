-- V427 created the alert "level" columns as SMALLINT, but the entities read them as int, and Hibernate's schema
-- validation refuses to start on that mismatch. Widen them to INTEGER (V427 itself is left untouched: it has already run).
ALTER TABLE earthmoving_alert_recipients ALTER COLUMN level TYPE INTEGER;
ALTER TABLE earthmoving_alerts           ALTER COLUMN level TYPE INTEGER;
ALTER TABLE earthmoving_alert_deliveries ALTER COLUMN level TYPE INTEGER;
