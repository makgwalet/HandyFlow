-- V53__fix_contract_parties_check.sql
--
-- ROOT CAUSE: Three constraint mismatches between the schema and the application:
--
-- 1. contract_parties.party_type
--    Schema had: CHECK (party_type IN ('INTERNAL','EXTERNAL'))
--    Application sends: 'INDIVIDUAL', 'COMPANY'
--    → Every AddParty call failed with constraint violation
--
-- 2. contract_parties.signing_status
--    Schema had: CHECK (signing_status IN ('PENDING','SENT','SIGNED','DECLINED'))
--    V53 previously tried to add 'CANCELLED' but missed 'SENT' — new constraint
--    violated existing rows that had signing_status = 'SENT'
--    → Flyway failed at startup (line 27 of previous V53)
--
-- 3. contract_type on both contracts and contract_templates
--    Schema had a fixed list missing: SERVICE_AGREEMENT, CONSULTING, RETAINER,
--    MAINTENANCE, LEASE — all used by the frontend CONTRACT_TYPES array
--    → Creating any contract with these types would fail
--
-- STRATEGY: Drop all three constraints without replacement first (unblocks existing rows),
-- then add corrected constraints with the complete value sets used by the application.

-- ── 1. contract_parties.party_type ───────────────────────────────────────────
-- Drop the old INTERNAL/EXTERNAL constraint
ALTER TABLE contract_parties
    DROP CONSTRAINT IF EXISTS contract_parties_party_type_check;

-- Add correct constraint matching the application's AddPartyRequest and frontend
ALTER TABLE contract_parties
    ADD CONSTRAINT contract_parties_party_type_check
        CHECK (party_type IN ('INDIVIDUAL','COMPANY','TRUST','GOVERNMENT','INTERNAL','EXTERNAL'));

-- ── 2. contract_parties.signing_status ───────────────────────────────────────
-- Drop the old constraint (whether it's from the original migration or the failed V53)
ALTER TABLE contract_parties
    DROP CONSTRAINT IF EXISTS contract_parties_signing_status_check;

-- Add corrected constraint — includes SENT (in original) + CANCELLED (new) + all existing
ALTER TABLE contract_parties
    ADD CONSTRAINT contract_parties_signing_status_check
        CHECK (signing_status IN ('PENDING','SENT','SIGNED','DECLINED','CANCELLED'));

-- ── 3. contract_type on contract_templates ───────────────────────────────────
-- The template seeder uses JOINT_VENTURE, EQUIPMENT_HIRE, NDA, SERVICE_LEVEL, SUBCONTRACTOR
-- The frontend CONTRACT_TYPES adds SERVICE_AGREEMENT, CONSULTING, RETAINER, LEASE, SUPPLY, etc.
ALTER TABLE contract_templates
    DROP CONSTRAINT IF EXISTS contract_templates_contract_type_check;

ALTER TABLE contract_templates
    ADD CONSTRAINT contract_templates_contract_type_check
        CHECK (contract_type IN (
            'JOINT_VENTURE','EQUIPMENT_HIRE','EQUIPMENT_LEASE','SERVICE_LEVEL',
            'NDA','SUPPLY','EMPLOYMENT','SUBCONTRACTOR','LOAN','PARTNERSHIP',
            'SERVICE_AGREEMENT','CONSULTING','RETAINER','MAINTENANCE','LEASE',
            'OTHER'
        ));

-- ── 4. contract_type on contracts ────────────────────────────────────────────
-- contracts.contract_type has no explicit CHECK in the schema (no REFERENCES to template type)
-- but add one now for data integrity, matching the same full list
ALTER TABLE contracts
    DROP CONSTRAINT IF EXISTS contracts_contract_type_check;

-- contracts table in the provided schema has no contract_type CHECK — safe to add fresh
-- (This ADD will be a no-op if it somehow doesn't exist, otherwise adds the guard)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'contracts_contract_type_check'
          AND conrelid = 'contracts'::regclass
    ) THEN
        ALTER TABLE contracts
            ADD CONSTRAINT contracts_contract_type_check
                CHECK (contract_type IN (
                    'JOINT_VENTURE','EQUIPMENT_HIRE','EQUIPMENT_LEASE','SERVICE_LEVEL',
                    'NDA','SUPPLY','EMPLOYMENT','SUBCONTRACTOR','LOAN','PARTNERSHIP',
                    'SERVICE_AGREEMENT','CONSULTING','RETAINER','MAINTENANCE','LEASE',
                    'OTHER'
                ));
    END IF;
END $$;
