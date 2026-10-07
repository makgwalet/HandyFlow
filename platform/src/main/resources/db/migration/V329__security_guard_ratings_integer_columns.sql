-- V327 declared the 1-5 rating columns as SMALLINT, but the JPA entity maps them
-- to int, so Hibernate schema validation failed at startup. Widen them to INTEGER.
-- (V327 is left untouched so databases that already applied it keep a valid checksum.)
ALTER TABLE security_guard_ratings
    ALTER COLUMN punctuality       TYPE INTEGER,
    ALTER COLUMN professionalism   TYPE INTEGER,
    ALTER COLUMN appearance        TYPE INTEGER,
    ALTER COLUMN communication     TYPE INTEGER,
    ALTER COLUMN alertness         TYPE INTEGER,
    ALTER COLUMN incident_handling TYPE INTEGER;
