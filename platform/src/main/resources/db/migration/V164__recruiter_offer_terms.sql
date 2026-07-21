-- V164__recruiter_offer_terms.sql
-- RENAME THIS FILE before running: replace __PLACEHOLDER__ with your actual
-- next available Flyway version number. This session only has visibility
-- into V40 (recruiter module) and V58 (accountant module) — not your full
-- migrations folder — so the real next number can't be determined here.
--
-- WHY these columns? Offer terms (salary, start date, benefits) previously
-- had nowhere to live until an applicant was converted to an HR employee —
-- which happens AFTER hire, too late to have generated an offer letter at
-- the point the offer was actually extended. This captures them at the
-- OFFER stage instead, via MoveStageRequest, so a real offer letter can be
-- generated and sent before the candidate has even accepted.

ALTER TABLE rec_applications
    ADD COLUMN offered_salary           NUMERIC(12,2),
    ADD COLUMN offered_salary_frequency VARCHAR(20),
    ADD COLUMN offered_start_date       DATE,
    ADD COLUMN offer_benefits           TEXT,
    ADD COLUMN offer_letter_sent_at     TIMESTAMP;