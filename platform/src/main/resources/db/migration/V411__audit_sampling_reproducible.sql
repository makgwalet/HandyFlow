-- V411: A10. A sampling plan now records how its population was defined and the seed used to draw the sample, so the draw
-- can be repeated and checked. Earlier plans leave both empty. Selection method is now honoured (RANDOM, SYSTEMATIC).
ALTER TABLE audit_sampling_plans
    ADD COLUMN population_definition TEXT,
    ADD COLUMN selection_seed        BIGINT,
    ADD COLUMN selection_start       INTEGER;

-- A9. Review notes on workpapers: raised by a reviewer, answered by the preparer, cleared by someone other than the person who
-- answered. A workpaper cannot be signed off while it has notes that are not cleared.
CREATE TABLE audit_review_notes (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID         NOT NULL,
    engagement_id  UUID         NOT NULL,
    workpaper_id   UUID         NOT NULL REFERENCES audit_workpaper_files (id),
    note           TEXT         NOT NULL,
    raised_by      UUID         NOT NULL,
    raised_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    status         VARCHAR(10)  NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESPONDED', 'CLEARED')),
    response       TEXT,
    responded_by   UUID,
    responded_at   TIMESTAMPTZ,
    cleared_by     UUID,
    cleared_at     TIMESTAMPTZ,
    times_returned INT          NOT NULL DEFAULT 0
);
CREATE INDEX idx_audit_review_notes_wp ON audit_review_notes (tenant_id, workpaper_id);

-- A9. What a workpaper supports: a test, an exception or a finding.
CREATE TABLE audit_workpaper_links (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID         NOT NULL,
    workpaper_id  UUID         NOT NULL REFERENCES audit_workpaper_files (id),
    link_type     VARCHAR(12)  NOT NULL CHECK (link_type IN ('TEST', 'EXCEPTION', 'FINDING')),
    link_id       UUID         NOT NULL,
    linked_by     UUID         NOT NULL,
    linked_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (workpaper_id, link_type, link_id)
);
CREATE INDEX idx_audit_workpaper_links_target ON audit_workpaper_links (tenant_id, link_type, link_id);
