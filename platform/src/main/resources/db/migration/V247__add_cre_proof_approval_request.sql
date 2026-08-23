-- NUMBERING NOTE: same placeholder caution as every migration this
-- session — confirm the real next version number before running; this
-- was written without a live view of your current flyway_schema_history.

-- backlog 1.1 (Creative migration onto the shared approval engine)
-- Links a proof to its ApprovalRequest in the approvals module. Nullable
-- and additive — existing proofs (and any created before this ships)
-- simply have no linked request; CreProof's own old
-- approvalToken/tokenExpiresAt/approvalMode columns are left in place,
-- untouched, unused by new code going forward (same "don't retroactively
-- drop columns" caution as every other migration this session) — this is
-- a full cutover for NEW proof-sending going forward, not a backfill of
-- historical data.
ALTER TABLE cre_proofs ADD COLUMN approval_request_id UUID;
CREATE INDEX idx_cre_proofs_approval_request ON cre_proofs (approval_request_id);