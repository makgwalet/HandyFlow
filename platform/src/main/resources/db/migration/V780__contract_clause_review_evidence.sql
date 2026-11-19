-- The reviewer's signed-off review of a clause, stored as a document (Documents module). Nullable: clauses approved before this
-- migration have no file, and attaching one is optional.
ALTER TABLE contract_clauses ADD COLUMN IF NOT EXISTS evidence_document_id UUID;
