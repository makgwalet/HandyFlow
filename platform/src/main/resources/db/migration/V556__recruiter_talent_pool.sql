-- Reusable lists of candidates a recruiter wants to remember, and free tags on a candidate. The candidate record itself is the existing one
-- (one per email per company), so a person is never duplicated across vacancies.
CREATE TABLE IF NOT EXISTS rec_talent_pools (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    name        VARCHAR(80) NOT NULL,
    description VARCHAR(300),
    created_by  UUID,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_rec_talent_pools_name ON rec_talent_pools (tenant_id, LOWER(name));

CREATE TABLE IF NOT EXISTS rec_pool_members (
    pool_id      UUID NOT NULL REFERENCES rec_talent_pools(id) ON DELETE CASCADE,
    applicant_id UUID NOT NULL REFERENCES rec_applicants(id) ON DELETE CASCADE,
    note         VARCHAR(300),
    added_by     UUID,
    added_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (pool_id, applicant_id)
);
CREATE INDEX IF NOT EXISTS idx_rec_pool_members_applicant ON rec_pool_members (applicant_id);

CREATE TABLE IF NOT EXISTS rec_applicant_tags (
    applicant_id UUID NOT NULL REFERENCES rec_applicants(id) ON DELETE CASCADE,
    tenant_id    UUID NOT NULL,
    tag          VARCHAR(30) NOT NULL,
    PRIMARY KEY (applicant_id, tag)
);
CREATE INDEX IF NOT EXISTS idx_rec_applicant_tags_tag ON rec_applicant_tags (tenant_id, tag);
