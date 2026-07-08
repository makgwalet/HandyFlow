-- V___creative_comment_annotation.sql
-- (rename to the next available Flyway version number in your sequence)
--
-- Backs point annotation on image proofs — a comment pinned to a specific
-- spot, not just a thread-level note. Stored as 0-1 fractions of the
-- image's width/height (not pixels) so a pin stays correct regardless of
-- what size the image is actually rendered at.

ALTER TABLE cre_proof_comments
    ADD COLUMN IF NOT EXISTS anchor_x DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS anchor_y DOUBLE PRECISION;
