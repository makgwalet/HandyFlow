-- V613 created unit_cost_currency as CHAR(3); the entity maps it as a string (VARCHAR), which Hibernate's schema validation rejects.
-- V613 is already applied on existing databases, so it is corrected here rather than edited.
ALTER TABLE whse_items ALTER COLUMN unit_cost_currency TYPE VARCHAR(3);
