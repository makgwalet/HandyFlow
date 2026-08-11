-- Rename this file to the actual next Flyway version before running.

ALTER TABLE customer_followups
    ADD COLUMN outcome             VARCHAR(20),
    ADD COLUMN rescheduled_from_id UUID REFERENCES customer_followups(id);