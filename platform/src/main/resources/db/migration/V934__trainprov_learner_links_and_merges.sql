-- Training Provider 0168: delegate links, duplicate learners and controlled merge.
-- A learner is never the same record as a delegate. The link is optional, has a history, and a delegate can be linked to
-- only one live learner. Two learner records for one person are joined by a merge that a second person approves.
-- Nothing is deleted: the merged learner is archived and points at the learner it was merged into.

ALTER TABLE trainprov_learners ADD COLUMN merged_into_id UUID REFERENCES trainprov_learners(id);
ALTER TABLE trainprov_learners ADD CONSTRAINT ck_trainprov_learner_merged CHECK (
    merged_into_id IS NULL OR (status = 'ARCHIVED' AND merged_into_id <> id));
CREATE INDEX idx_trainprov_learner_merged ON trainprov_learners (tenant_id, merged_into_id) WHERE merged_into_id IS NOT NULL;

-- One delegate, one live learner. Links made before this patch were not checked, so the index is only created when the
-- existing data already agrees. If it does not, the offenders are listed by db/audit/trainprov_learner_delegate_links.sql,
-- nothing is changed, and the service enforces the rule on every new link either way.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM trainprov_learners WHERE delegate_id IS NOT NULL AND status = 'ACTIVE'
               GROUP BY tenant_id, delegate_id HAVING COUNT(*) > 1) THEN
        RAISE WARNING 'trainprov: some delegates are linked to more than one live learner. uq_trainprov_learner_delegate was NOT created. Run db/audit/trainprov_learner_delegate_links.sql';
    ELSE
        CREATE UNIQUE INDEX uq_trainprov_learner_delegate ON trainprov_learners (tenant_id, delegate_id)
            WHERE delegate_id IS NOT NULL AND status = 'ACTIVE';
    END IF;
END $$;

-- What happened to a learner's links and merges. Written once, never changed.
CREATE TABLE trainprov_learner_events (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    learner_id       UUID NOT NULL REFERENCES trainprov_learners(id),
    event_type       VARCHAR(30) NOT NULL CHECK (event_type IN ('DELEGATE_LINKED', 'DELEGATE_UNLINKED', 'MERGED_INTO', 'MERGED_FROM')),
    delegate_id      UUID REFERENCES trainprov_delegates(id),
    other_learner_id UUID REFERENCES trainprov_learners(id),
    reason           TEXT,
    acted_by         UUID NOT NULL,
    acted_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_trainprov_lev_shape CHECK (
        (event_type IN ('DELEGATE_LINKED', 'DELEGATE_UNLINKED') AND delegate_id IS NOT NULL AND other_learner_id IS NULL)
        OR (event_type IN ('MERGED_INTO', 'MERGED_FROM') AND other_learner_id IS NOT NULL AND delegate_id IS NULL)),
    CONSTRAINT ck_trainprov_lev_reason CHECK (
        event_type = 'DELEGATE_LINKED' OR COALESCE(char_length(btrim(reason)), 0) >= 10)
);
CREATE INDEX idx_trainprov_lev_learner ON trainprov_learner_events (tenant_id, learner_id, acted_at);

-- A proposed or finished merge of two learner records. Both records are copied here as they were on the day.
CREATE TABLE trainprov_learner_merges (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    survivor_id         UUID NOT NULL REFERENCES trainprov_learners(id),
    merged_id           UUID NOT NULL REFERENCES trainprov_learners(id),
    status              VARCHAR(20) NOT NULL DEFAULT 'PROPOSED' CHECK (status IN ('PROPOSED', 'COMPLETED', 'REJECTED', 'CANCELLED')),
    match_reasons       TEXT,
    conflicts           TEXT,
    conflicts_accepted  BOOLEAN NOT NULL DEFAULT FALSE,
    reason              TEXT NOT NULL,
    survivor_snapshot   TEXT NOT NULL,
    merged_snapshot     TEXT NOT NULL,
    proposed_by         UUID NOT NULL,
    proposed_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_by          UUID,
    decided_at          TIMESTAMPTZ,
    decision_reason     TEXT,
    confirmed_alone     BOOLEAN NOT NULL DEFAULT FALSE,
    moved_enrolments    INTEGER,
    moved_applications  INTEGER,
    moved_documents     INTEGER,
    delegate_note       TEXT,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_trainprov_merge_distinct CHECK (survivor_id <> merged_id),
    CONSTRAINT ck_trainprov_merge_reason CHECK (COALESCE(char_length(btrim(reason)), 0) >= 10),
    CONSTRAINT ck_trainprov_merge_conflicts CHECK (conflicts IS NULL OR conflicts_accepted = TRUE),
    CONSTRAINT ck_trainprov_merge_decided CHECK (
        (status = 'PROPOSED' AND decided_by IS NULL AND decided_at IS NULL)
        OR (status <> 'PROPOSED' AND decided_by IS NOT NULL AND decided_at IS NOT NULL AND COALESCE(char_length(btrim(decision_reason)), 0) >= 10)),
    CONSTRAINT ck_trainprov_merge_second CHECK (
        status NOT IN ('COMPLETED', 'REJECTED') OR decided_by <> proposed_by OR confirmed_alone = TRUE),
    CONSTRAINT ck_trainprov_merge_counts CHECK (
        status <> 'COMPLETED' OR (moved_enrolments IS NOT NULL AND moved_applications IS NOT NULL AND moved_documents IS NOT NULL))
);
CREATE INDEX idx_trainprov_merge_tenant ON trainprov_learner_merges (tenant_id, status, proposed_at);
CREATE INDEX idx_trainprov_merge_survivor ON trainprov_learner_merges (tenant_id, survivor_id);
CREATE UNIQUE INDEX uq_trainprov_merge_open ON trainprov_learner_merges (tenant_id, merged_id) WHERE status = 'PROPOSED';
