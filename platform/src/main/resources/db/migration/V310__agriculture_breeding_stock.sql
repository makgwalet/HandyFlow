-- V310__agriculture_breeding_stock.sql
--
-- ADR-001 decision 7: buying a bull is capital, not a direct cost of one batch. An animal flagged as breeding stock keeps its purchase price OUT of the
-- gross-margin report (its running costs and any sales still count, and it is shown in its own row, apart from the production margins).
-- The flag only changes how margins are REPORTED. It never edits a cost, a sale or the animal's own purchase price. Default false, so every
-- existing animal is reported exactly as before until someone flags it.
ALTER TABLE ag_animals ADD COLUMN breeding_stock BOOLEAN NOT NULL DEFAULT FALSE;
