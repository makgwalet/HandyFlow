-- ============================================================================
-- Legal / Compliance — declaration campaigns: people who have left (or moved out of the audience) while a campaign was running. Depends on V815 (confirm the next free Flyway version before applying).
--
-- A return can now be LEFT: the person is no longer in the audience, so they are excused. LEFT returns are kept (nothing is deleted) but are not chased and are not counted in the
-- campaign's totals or percentages. Only a PENDING return can become LEFT; an answered one is a record and stays as it is.
-- ============================================================================
ALTER TABLE legalcompliance_declaration_returns DROP CONSTRAINT ck_lc_decret_status;
ALTER TABLE legalcompliance_declaration_returns ADD CONSTRAINT ck_lc_decret_status CHECK (status IN ('PENDING','NIL','UNCHANGED','DECLARED','LEFT'));
