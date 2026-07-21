-- V__PLACEHOLDER2__recruiter_interview_location.sql
-- RENAME THIS FILE: replace __PLACEHOLDER2__ with your actual next available
-- Flyway version number (must be higher than whatever
-- V__PLACEHOLDER__recruiter_offer_terms.sql was renamed to, if that hasn't
-- run yet — check flyway_schema_history to be sure).
--
-- WHY? No field anywhere captures a venue address (for IN_PERSON/PANEL) or
-- a meeting link (for VIDEO, e.g. Google Meet / MS Teams / Zoom) — flagged
-- during manual testing, confirmed as a real gap: neither the interviewer
-- notification nor the applicant confirmation email can currently say
-- where or how to join, because the data doesn't exist to say it with.

ALTER TABLE rec_interviews
    ADD COLUMN location TEXT;