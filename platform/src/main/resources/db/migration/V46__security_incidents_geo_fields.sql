-- V46__security_incidents_geo_shift.sql
ALTER TABLE security_incidents
    ADD COLUMN IF NOT EXISTS latitude   DECIMAL(10, 7),
    ADD COLUMN IF NOT EXISTS longitude  DECIMAL(10, 7),
    ADD COLUMN IF NOT EXISTS shift_id   UUID;