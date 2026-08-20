-- V235__add_pay_client_address_logo.sql
--
-- Two additions to PayClient:
-- 1. address — confirmed via real source PayClient had no address field
--    at all. Single free-text column, matching the same low-friction
--    shape already established for the `notes` column.
-- 2. logo_evidence_id — the client's logo, stored via EvidenceFacade
--    (Stage 0's Evidence Layer) — the third real consumer of that
--    pattern, same "store the evidenceId, not a raw storage key" shape
--    already proven with Recruitment Agency's CV migration.
ALTER TABLE pay_clients ADD COLUMN address TEXT;
ALTER TABLE pay_clients ADD COLUMN logo_evidence_id UUID;