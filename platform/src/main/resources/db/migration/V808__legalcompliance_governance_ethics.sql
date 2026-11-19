-- ============================================================================
-- Legal / Compliance — governance committees (engine 16) and conflict of interest & ethics (engine 18).
-- Depends on V291. Confirm the next free Flyway version before applying.
--
--  * Meetings record who attended; quorum is worked out from attendance against the committee's minimum, and decisions/actions can only be recorded
--    for a meeting that was held with a quorum. Approved minutes are immutable. The person who wrote the minutes cannot approve them.
--  * Declarations are reviewed by someone other than the declarant. Gifts and hospitality above the threshold in force on the day (stored on the row,
--    so changing the threshold later does not rewrite history) need approval by someone other than the person who received/gave them.
-- ============================================================================

CREATE TABLE legalcompliance_committees (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    reference               VARCHAR(20) NOT NULL,
    name                    VARCHAR(255) NOT NULL,
    committee_type          VARCHAR(15) NOT NULL,
    charter                 TEXT,
    meeting_frequency_months INT NOT NULL DEFAULT 3,
    quorum_min              INT NOT NULL DEFAULT 2,
    charter_review_months   INT NOT NULL DEFAULT 12,
    last_charter_review     DATE,
    status                  VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    dissolved_reason        TEXT,
    created_by              UUID,
    created_by_name         VARCHAR(255),
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lc_committee_ref UNIQUE (tenant_id, reference),
    CONSTRAINT ck_lc_committee_type CHECK (committee_type IN ('BOARD','AUDIT','RISK','SOCIAL_ETHICS','COMPLIANCE','OTHER')),
    CONSTRAINT ck_lc_committee_status CHECK (status IN ('ACTIVE','DISSOLVED')),
    CONSTRAINT ck_lc_committee_freq CHECK (meeting_frequency_months BETWEEN 1 AND 12),
    CONSTRAINT ck_lc_committee_quorum CHECK (quorum_min >= 1),
    CONSTRAINT ck_lc_committee_charter CHECK (charter_review_months BETWEEN 1 AND 60)
);
CREATE INDEX idx_lc_committee_tenant ON legalcompliance_committees (tenant_id, status);

CREATE TABLE legalcompliance_committee_members (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    committee_id  UUID NOT NULL REFERENCES legalcompliance_committees(id) ON DELETE CASCADE,
    member_name   VARCHAR(255) NOT NULL,
    user_id       UUID,
    member_role   VARCHAR(10) NOT NULL,
    appointed_on  DATE NOT NULL,
    ended_on      DATE,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    version       BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_lc_member_role CHECK (member_role IN ('CHAIR','MEMBER','SECRETARY','OBSERVER')),
    CONSTRAINT ck_lc_member_dates CHECK (ended_on IS NULL OR ended_on >= appointed_on)
);
CREATE INDEX idx_lc_member_committee ON legalcompliance_committee_members (committee_id);
CREATE UNIQUE INDEX uq_lc_member_one_chair ON legalcompliance_committee_members (committee_id) WHERE member_role = 'CHAIR' AND ended_on IS NULL;

CREATE TABLE legalcompliance_committee_meetings (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    committee_id            UUID NOT NULL REFERENCES legalcompliance_committees(id) ON DELETE CASCADE,
    meeting_date            DATE NOT NULL,
    title                   VARCHAR(255) NOT NULL,
    agenda                  TEXT,
    status                  VARCHAR(10) NOT NULL DEFAULT 'SCHEDULED',
    attendance              JSONB,
    present_count           INT,
    quorum_met              BOOLEAN,
    cancel_reason           TEXT,
    minutes                 TEXT,
    minutes_status          VARCHAR(10) NOT NULL DEFAULT 'NONE',
    minutes_by              UUID,
    minutes_by_name         VARCHAR(255),
    minutes_approved_by_name VARCHAR(255),
    minutes_approved_on     DATE,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_lc_meeting_status CHECK (status IN ('SCHEDULED','HELD','CANCELLED')),
    CONSTRAINT ck_lc_meeting_minutes CHECK (minutes_status IN ('NONE','DRAFT','APPROVED'))
);
CREATE INDEX idx_lc_meeting_committee ON legalcompliance_committee_meetings (committee_id, meeting_date DESC);

CREATE TABLE legalcompliance_committee_resolutions (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    meeting_id    UUID NOT NULL REFERENCES legalcompliance_committee_meetings(id) ON DELETE CASCADE,
    committee_id  UUID NOT NULL,
    kind          VARCHAR(10) NOT NULL,
    description   TEXT NOT NULL,
    owner_name    VARCHAR(255),
    due_date      DATE,
    status        VARCHAR(10) NOT NULL DEFAULT 'OPEN',
    completed_on  DATE,
    note          TEXT,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    version       BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_lc_resolution_kind CHECK (kind IN ('DECISION','ACTION')),
    CONSTRAINT ck_lc_resolution_status CHECK (status IN ('OPEN','DONE')),
    CONSTRAINT ck_lc_resolution_action CHECK (kind = 'DECISION' OR (owner_name IS NOT NULL AND due_date IS NOT NULL))
);
CREATE INDEX idx_lc_resolution_meeting ON legalcompliance_committee_resolutions (meeting_id);
CREATE INDEX idx_lc_resolution_due ON legalcompliance_committee_resolutions (due_date) WHERE status = 'OPEN' AND kind = 'ACTION';

CREATE TABLE legalcompliance_coi_declarations (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    reference           VARCHAR(20) NOT NULL,
    declarant_id        UUID NOT NULL,
    declarant_name      VARCHAR(255) NOT NULL,
    declaration_type    VARCHAR(20) NOT NULL,
    counterparty        VARCHAR(255) NOT NULL,
    description         TEXT NOT NULL,
    estimated_value     NUMERIC(14,2),
    declared_on         DATE NOT NULL,
    status              VARCHAR(15) NOT NULL DEFAULT 'DECLARED',
    reviewer_id         UUID,
    reviewer_name       VARCHAR(255),
    reviewed_on         DATE,
    decision_note       TEXT,
    mitigation          TEXT,
    review_months       INT NOT NULL DEFAULT 12,
    next_review_due     DATE,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lc_coi_ref UNIQUE (tenant_id, reference),
    CONSTRAINT ck_lc_coi_type CHECK (declaration_type IN ('OUTSIDE_EMPLOYMENT','DIRECTORSHIP','FINANCIAL_INTEREST','RELATED_PARTY','FAMILY_RELATIONSHIP','OTHER')),
    CONSTRAINT ck_lc_coi_status CHECK (status IN ('DECLARED','UNDER_REVIEW','CLEARED','MITIGATED','NOT_PERMITTED','WITHDRAWN')),
    CONSTRAINT ck_lc_coi_value CHECK (estimated_value IS NULL OR estimated_value >= 0),
    CONSTRAINT ck_lc_coi_months CHECK (review_months BETWEEN 1 AND 36)
);
CREATE INDEX idx_lc_coi_tenant ON legalcompliance_coi_declarations (tenant_id, status);
CREATE INDEX idx_lc_coi_declarant ON legalcompliance_coi_declarations (tenant_id, declarant_id);

CREATE TABLE legalcompliance_gift_entries (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    reference           VARCHAR(20) NOT NULL,
    direction           VARCHAR(10) NOT NULL,
    declarant_id        UUID NOT NULL,
    declarant_name      VARCHAR(255) NOT NULL,
    counterparty        VARCHAR(255) NOT NULL,
    description         TEXT NOT NULL,
    occasion            VARCHAR(255),
    entry_date          DATE NOT NULL,
    value_zar           NUMERIC(12,2) NOT NULL,
    threshold_zar       NUMERIC(12,2) NOT NULL,
    status              VARCHAR(10) NOT NULL,
    decided_by_name     VARCHAR(255),
    decided_on          DATE,
    decision_note       TEXT,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lc_gift_ref UNIQUE (tenant_id, reference),
    CONSTRAINT ck_lc_gift_direction CHECK (direction IN ('RECEIVED','GIVEN')),
    CONSTRAINT ck_lc_gift_status CHECK (status IN ('RECORDED','PENDING','APPROVED','DECLINED')),
    CONSTRAINT ck_lc_gift_value CHECK (value_zar >= 0 AND threshold_zar >= 0)
);
CREATE INDEX idx_lc_gift_tenant ON legalcompliance_gift_entries (tenant_id, status);

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'LEGALCOMPLIANCE_GOVERNANCE_MANAGE', 'Manage governance committees, members, meetings, minutes and resolutions'),
    (gen_random_uuid(), 'LEGALCOMPLIANCE_ETHICS_REVIEW', 'Review conflict-of-interest declarations, approve gifts and hospitality, and see everyone''s declarations')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name IN ('LEGALCOMPLIANCE_GOVERNANCE_MANAGE', 'LEGALCOMPLIANCE_ETHICS_REVIEW')
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
