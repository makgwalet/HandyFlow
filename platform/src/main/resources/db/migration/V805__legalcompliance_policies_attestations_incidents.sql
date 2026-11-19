-- ============================================================================
-- Legal / Compliance — Phase 4, part 1: policies, attestations and incidents / breaches.
-- Depends on V288. Confirm the next free Flyway version before applying.
--
--  * A policy has numbered versions. At most one version is PUBLISHED and at most one is open (DRAFT / IN_REVIEW / APPROVED) at a time.
--    The content of a version cannot be changed once it leaves DRAFT (domain rule, and a trigger as a backstop).
--  * An attestation campaign is pinned to one PUBLISHED version and records that version's content hash, so "who accepted what" stays provable.
--  * Incidents may carry corrective / preventive actions: legalcompliance_capas gains a third possible parent (incident_id).
-- ============================================================================

CREATE TABLE legalcompliance_policies (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    reference            VARCHAR(20) NOT NULL,
    title                VARCHAR(255) NOT NULL,
    policy_type          VARCHAR(10) NOT NULL,
    owner_id             UUID,
    owner_name           VARCHAR(255) NOT NULL,
    review_months        INTEGER NOT NULL DEFAULT 12,
    status               VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    published_version_id UUID,
    next_review_due      DATE,
    retired_reason       TEXT,
    retired_at           TIMESTAMPTZ,
    created_by           UUID,
    created_by_name      VARCHAR(255),
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL,
    version              BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lc_policy_ref UNIQUE (tenant_id, reference),
    CONSTRAINT ck_lc_policy_type CHECK (policy_type IN ('POLICY','PROCEDURE','STANDARD')),
    CONSTRAINT ck_lc_policy_status CHECK (status IN ('ACTIVE','RETIRED')),
    CONSTRAINT ck_lc_policy_review CHECK (review_months BETWEEN 1 AND 60)
);
CREATE INDEX idx_lc_policy_tenant ON legalcompliance_policies (tenant_id, status);

CREATE TABLE legalcompliance_policy_versions (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    policy_id         UUID NOT NULL REFERENCES legalcompliance_policies (id),
    version_number    INTEGER NOT NULL,
    status            VARCHAR(12) NOT NULL,
    content           TEXT NOT NULL,
    change_summary    TEXT,
    author_id         UUID,
    author_name       VARCHAR(255),
    submitted_at      TIMESTAMPTZ,
    content_hash      VARCHAR(64),
    approved_by       UUID,
    approved_by_name  VARCHAR(255),
    approved_at       TIMESTAMPTZ,
    review_note       TEXT,
    returned_count    INTEGER NOT NULL DEFAULT 0,
    effective_date    DATE,
    published_by_name VARCHAR(255),
    published_at      TIMESTAMPTZ,
    superseded_at     TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lc_pver_number UNIQUE (policy_id, version_number),
    CONSTRAINT ck_lc_pver_status CHECK (status IN ('DRAFT','IN_REVIEW','APPROVED','PUBLISHED','SUPERSEDED'))
);
CREATE UNIQUE INDEX uq_lc_pver_one_open ON legalcompliance_policy_versions (policy_id) WHERE status IN ('DRAFT','IN_REVIEW','APPROVED');
CREATE UNIQUE INDEX uq_lc_pver_one_published ON legalcompliance_policy_versions (policy_id) WHERE status = 'PUBLISHED';

CREATE FUNCTION lc_policy_version_immutable() RETURNS trigger AS $$
BEGIN
    IF OLD.status IN ('IN_REVIEW','APPROVED','PUBLISHED','SUPERSEDED') AND NEW.content IS DISTINCT FROM OLD.content THEN
        RAISE EXCEPTION 'Policy version content cannot change once submitted for review (version %)', OLD.id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_lc_policy_version_immutable BEFORE UPDATE ON legalcompliance_policy_versions
    FOR EACH ROW EXECUTE FUNCTION lc_policy_version_immutable();

CREATE TABLE legalcompliance_attestation_campaigns (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    reference         VARCHAR(20) NOT NULL,
    policy_id         UUID NOT NULL REFERENCES legalcompliance_policies (id),
    policy_version_id UUID NOT NULL REFERENCES legalcompliance_policy_versions (id),
    title             VARCHAR(255) NOT NULL,
    instructions      TEXT,
    due_date          DATE NOT NULL,
    department_filter VARCHAR(100),
    status            VARCHAR(10) NOT NULL,
    content_hash      VARCHAR(64) NOT NULL,
    launched_by       UUID,
    launched_by_name  VARCHAR(255),
    closed_at         TIMESTAMPTZ,
    closed_by_name    VARCHAR(255),
    close_reason      TEXT,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lc_campaign_ref UNIQUE (tenant_id, reference),
    CONSTRAINT ck_lc_campaign_status CHECK (status IN ('OPEN','CLOSED'))
);
CREATE UNIQUE INDEX uq_lc_campaign_one_open ON legalcompliance_attestation_campaigns (policy_version_id) WHERE status = 'OPEN';
CREATE INDEX idx_lc_campaign_tenant ON legalcompliance_attestation_campaigns (tenant_id, status, due_date);

CREATE TABLE legalcompliance_attestations (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    campaign_id   UUID NOT NULL REFERENCES legalcompliance_attestation_campaigns (id),
    user_id       UUID NOT NULL,
    user_name     VARCHAR(255) NOT NULL,
    user_email    VARCHAR(255),
    status        VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    responded_at  TIMESTAMPTZ,
    comment       TEXT,
    content_hash  VARCHAR(64),
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    version       BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lc_attestation_user UNIQUE (campaign_id, user_id),
    CONSTRAINT ck_lc_attestation_status CHECK (status IN ('PENDING','ACCEPTED','DECLINED'))
);
CREATE INDEX idx_lc_attestation_user ON legalcompliance_attestations (tenant_id, user_id, status);

CREATE TABLE legalcompliance_incidents (
    id                          UUID PRIMARY KEY,
    tenant_id                   UUID NOT NULL,
    reference                   VARCHAR(20) NOT NULL,
    incident_type               VARCHAR(20) NOT NULL,
    title                       VARCHAR(255) NOT NULL,
    description                 TEXT NOT NULL,
    severity                    VARCHAR(10) NOT NULL,
    status                      VARCHAR(15) NOT NULL,
    occurred_at                 TIMESTAMPTZ,
    discovered_at               TIMESTAMPTZ NOT NULL,
    reported_by                 UUID,
    reported_by_name            VARCHAR(255),
    control_id                  UUID REFERENCES legalcompliance_controls (id),
    personal_info_involved      BOOLEAN NOT NULL DEFAULT FALSE,
    data_subjects_affected      INTEGER,
    data_categories             TEXT,
    investigator_id             UUID,
    investigator_name           VARCHAR(255),
    containment_actions         TEXT,
    root_cause                  TEXT,
    resolution_note             TEXT,
    resolved_at                 TIMESTAMPTZ,
    notification_assessed_at    TIMESTAMPTZ,
    notification_assessed_by    VARCHAR(255),
    notification_required       BOOLEAN,
    notification_rationale      TEXT,
    regulator_notified_at       TIMESTAMPTZ,
    regulator_reference         VARCHAR(100),
    subjects_notified_at        TIMESTAMPTZ,
    subjects_not_notified_reason TEXT,
    closed_by                   UUID,
    closed_by_name              VARCHAR(255),
    closed_at                   TIMESTAMPTZ,
    closure_note                TEXT,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lc_incident_ref UNIQUE (tenant_id, reference),
    CONSTRAINT ck_lc_incident_type CHECK (incident_type IN ('PRIVACY_BREACH','SECURITY','REGULATORY','OPERATIONAL','ETHICAL')),
    CONSTRAINT ck_lc_incident_sev CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT ck_lc_incident_status CHECK (status IN ('REPORTED','INVESTIGATING','RESOLVED','CLOSED')),
    CONSTRAINT ck_lc_incident_when CHECK (occurred_at IS NULL OR occurred_at <= discovered_at)
);
CREATE INDEX idx_lc_incident_tenant ON legalcompliance_incidents (tenant_id, status, discovered_at DESC);

-- CAPA: a third possible parent.
ALTER TABLE legalcompliance_capas ADD COLUMN incident_id UUID REFERENCES legalcompliance_incidents (id);
ALTER TABLE legalcompliance_capas DROP CONSTRAINT ck_lc_capa_one_parent;
ALTER TABLE legalcompliance_capas ADD CONSTRAINT ck_lc_capa_one_parent CHECK (
    (finding_id IS NOT NULL)::int + (audit_finding_id IS NOT NULL)::int + (incident_id IS NOT NULL)::int = 1);
CREATE INDEX idx_lc_capa_incident ON legalcompliance_capas (incident_id) WHERE incident_id IS NOT NULL;

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'LEGALCOMPLIANCE_POLICY_MANAGE',      'Draft, submit, publish and retire policies'),
    (gen_random_uuid(), 'LEGALCOMPLIANCE_POLICY_APPROVE',     'Approve or return policy versions; cannot approve your own'),
    (gen_random_uuid(), 'LEGALCOMPLIANCE_ATTESTATION_MANAGE', 'Launch and close attestation campaigns and see who has responded'),
    (gen_random_uuid(), 'LEGALCOMPLIANCE_INCIDENT_MANAGE',    'Investigate, assess, notify and close incidents; cannot close one you investigated')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name IN ('LEGALCOMPLIANCE_POLICY_MANAGE','LEGALCOMPLIANCE_POLICY_APPROVE','LEGALCOMPLIANCE_ATTESTATION_MANAGE','LEGALCOMPLIANCE_INCIDENT_MANAGE')
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
