-- ============================================================================
-- Legal / Compliance — the chair's casting vote on a tied ordinary resolution. Depends on V814 (confirm the next free Flyway version before applying).
--
-- A casting vote is recorded on the ballot beside the tally, never inside it: the counts stay exactly as voted. It can exist only on a CLOSED ballot, must be FOR or
-- AGAINST, and must carry a reason (what the charter or standing orders say). The name is the chair's, as a casting vote is minuted.
-- ============================================================================
ALTER TABLE legalcompliance_resolution_ballots
    ADD COLUMN casting_vote    VARCHAR(7),
    ADD COLUMN casting_by_name VARCHAR(255),
    ADD COLUMN casting_reason  VARCHAR(500);

ALTER TABLE legalcompliance_resolution_ballots
    ADD CONSTRAINT ck_lc_ballot_casting CHECK (
        casting_vote IS NULL
        OR (status = 'CLOSED' AND casting_vote IN ('FOR','AGAINST') AND casting_by_name IS NOT NULL AND casting_reason IS NOT NULL AND length(trim(casting_reason)) >= 10));
