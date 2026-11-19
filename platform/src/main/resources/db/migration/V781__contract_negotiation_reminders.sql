-- The day staff were last reminded about this negotiation (deadline close or passed, or proposals waiting for a decision).
ALTER TABLE contract_negotiations ADD COLUMN IF NOT EXISTS last_reminded_on DATE;
