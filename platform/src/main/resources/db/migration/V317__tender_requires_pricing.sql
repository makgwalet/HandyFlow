-- Whether a tender needs a price before it can be submitted. Until now this was a tick-box on each package build;
-- it is a fact about the tender, so it is kept on the tender.
ALTER TABLE tenders ADD COLUMN requires_pricing BOOLEAN NOT NULL DEFAULT FALSE;
