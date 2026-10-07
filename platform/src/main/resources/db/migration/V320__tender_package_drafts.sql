-- The choices a person has made on the package screen for a tender (cover letter, sections, documents, rules), kept so they
-- survive a reload. One draft per tender; the last save wins. The shape belongs to the screen, so it is stored as JSON text.
CREATE TABLE tender_package_drafts (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    tender_id   UUID NOT NULL REFERENCES tenders(id),
    data        TEXT NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by  UUID,
    updated_by_name VARCHAR(200)
);
CREATE UNIQUE INDEX uq_tender_package_drafts_tender ON tender_package_drafts(tenant_id, tender_id);
