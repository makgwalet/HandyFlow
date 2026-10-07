-- V337__security_guard_reviews_integer_scores.sql
--
-- V334 created the six review scores as SMALLINT, but the GuardReview entity maps them as int, so Hibernate's
-- schema validation refused to start the app ("found int2, expecting integer"). Make the columns INTEGER.
-- The 1..5 CHECK constraints are kept as they are. V334 is already applied, so it is not edited.

ALTER TABLE security_guard_reviews
    ALTER COLUMN punctuality       TYPE INTEGER,
    ALTER COLUMN professionalism   TYPE INTEGER,
    ALTER COLUMN appearance        TYPE INTEGER,
    ALTER COLUMN communication     TYPE INTEGER,
    ALTER COLUMN alertness         TYPE INTEGER,
    ALTER COLUMN incident_handling TYPE INTEGER;
