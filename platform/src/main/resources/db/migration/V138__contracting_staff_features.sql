-- V___contracting_staff_features.sql
-- (rename to the next available Flyway version number in your sequence)
--
-- Backs two features that ContractsTab.tsx (the staff-facing UI) already
-- called, with zero backend support existing for either:
--   - staff-witnessed in-person signing (ContractSignature.witnessedByUserId)
--   - internal/staff comments (ContractComment.postedByUserId)
-- ContractingService.signContract() and the partyId-nullable design of
-- ContractComment already existed and were ready for this — only the
-- columns, service wiring, and controller endpoints were missing.

ALTER TABLE contract_signatures
    ADD COLUMN IF NOT EXISTS witnessed_by_user_id UUID;

ALTER TABLE contract_comments
    ADD COLUMN IF NOT EXISTS posted_by_user_id UUID;
