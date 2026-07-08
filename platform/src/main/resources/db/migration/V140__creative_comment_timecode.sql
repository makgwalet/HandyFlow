-- V___creative_comment_timecode.sql
-- (rename to the next available Flyway version number in your sequence)
--
-- Backs video timecode commenting — "at 0:45 the logo should be bigger" is
-- meaningless without a way to record which 0:45. Null for comments on
-- non-video proofs or comments not tied to a specific moment.

ALTER TABLE cre_proof_comments
    ADD COLUMN IF NOT EXISTS timecode_seconds DOUBLE PRECISION;
