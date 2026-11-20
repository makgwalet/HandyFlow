-- V827: Legal Practice — client due diligence (FICA intake). Confirm the next free Flyway version (V826 is LP chunk 6).
--
-- One profile per due-diligence cycle for a client. A profile is prepared (DRAFT), submitted once it is complete, and approved
-- by a second person (VERIFIED), which sets the date it must be reviewed by. A review starts a new profile; earlier ones are
-- kept, so the firm can show what it knew about a client at any time. Profiles are never deleted (records are kept for at
-- least five years after the relationship ends). Parties and items can only change while their profile is a draft.
-- The firm decides, in writing, whether matters and trust receipts are blocked until a client is verified (lp_kyc_setting_changes).

CREATE TABLE IF NOT EXISTS lp_kyc_profiles (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID         NOT NULL,
    client_id          UUID         NOT NULL,
    subject_kind       VARCHAR(12)  NOT NULL,
    status             VARCHAR(10)  NOT NULL DEFAULT 'DRAFT',
    risk_geography     VARCHAR(6),
    risk_service       VARCHAR(6),
    risk_client        VARCHAR(6),
    risk_channel       VARCHAR(6),
    risk_rating        VARCHAR(6),
    pep_status         VARCHAR(5),
    pep_checked_on     DATE,
    pep_note           TEXT,
    sanctions_checked_on DATE,
    sanctions_result   VARCHAR(5),
    business_nature    TEXT,
    source_of_funds    TEXT,
    source_of_wealth   TEXT,
    started_by         UUID         NOT NULL,
    started_by_name    VARCHAR(255),
    started_at         TIMESTAMPTZ  NOT NULL,
    submitted_by       UUID,
    submitted_by_name  VARCHAR(255),
    submitted_at       TIMESTAMPTZ,
    return_reason      TEXT,
    approved_by        UUID,
    approved_by_name   VARCHAR(255),
    approved_at        TIMESTAMPTZ,
    approval_note      TEXT,
    self_approved      BOOLEAN      NOT NULL DEFAULT FALSE,
    senior_approved    BOOLEAN      NOT NULL DEFAULT FALSE,
    valid_until        DATE,
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_lp_kyc_subject CHECK (subject_kind IN ('INDIVIDUAL','COMPANY','TRUST','PARTNERSHIP')),
    CONSTRAINT ck_lp_kyc_status  CHECK (status IN ('DRAFT','SUBMITTED','VERIFIED')),
    CONSTRAINT ck_lp_kyc_risk    CHECK (risk_rating IS NULL OR risk_rating IN ('LOW','MEDIUM','HIGH')),
    CONSTRAINT ck_lp_kyc_riskin  CHECK ((risk_geography IS NULL OR risk_geography IN ('LOW','MEDIUM','HIGH')) AND (risk_service IS NULL OR risk_service IN ('LOW','MEDIUM','HIGH'))
                                     AND (risk_client IS NULL OR risk_client IN ('LOW','MEDIUM','HIGH')) AND (risk_channel IS NULL OR risk_channel IN ('LOW','MEDIUM','HIGH'))),
    CONSTRAINT ck_lp_kyc_pep     CHECK (pep_status IS NULL OR pep_status IN ('NONE','DPEP','PIP','FPEP')),
    CONSTRAINT ck_lp_kyc_sanc    CHECK (sanctions_result IS NULL OR sanctions_result IN ('CLEAR','MATCH')),
    -- A submitted or verified profile is complete in the ways the database can see.
    CONSTRAINT ck_lp_kyc_submitted CHECK (status = 'DRAFT' OR (submitted_by IS NOT NULL AND submitted_at IS NOT NULL AND risk_rating IS NOT NULL
                                      AND pep_status IS NOT NULL AND pep_checked_on IS NOT NULL AND sanctions_result = 'CLEAR' AND sanctions_checked_on IS NOT NULL
                                      AND length(btrim(coalesce(business_nature,''))) >= 10)),
    CONSTRAINT ck_lp_kyc_verified  CHECK (status <> 'VERIFIED' OR (approved_by IS NOT NULL AND approved_at IS NOT NULL AND valid_until IS NOT NULL)),
    -- A different person approves, unless the firm allows self-approval, and never for high risk or a politically exposed person.
    CONSTRAINT ck_lp_kyc_segregation CHECK (approved_by IS NULL OR approved_by <> submitted_by OR (self_approved AND risk_rating <> 'HIGH' AND pep_status = 'NONE')),
    CONSTRAINT ck_lp_kyc_senior CHECK (status <> 'VERIFIED' OR (risk_rating <> 'HIGH' AND pep_status = 'NONE') OR senior_approved),
    CONSTRAINT ck_lp_kyc_hrsource CHECK (status = 'DRAFT' OR (risk_rating <> 'HIGH' AND pep_status = 'NONE')
                                      OR (length(btrim(coalesce(source_of_funds,''))) >= 10 AND length(btrim(coalesce(source_of_wealth,''))) >= 10))
);

CREATE INDEX IF NOT EXISTS idx_lp_kyc_client ON lp_kyc_profiles (tenant_id, client_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_lp_kyc_status ON lp_kyc_profiles (tenant_id, status);
-- One cycle in progress per client.
CREATE UNIQUE INDEX IF NOT EXISTS uq_lp_kyc_open ON lp_kyc_profiles (tenant_id, client_id) WHERE status IN ('DRAFT','SUBMITTED');

CREATE TABLE IF NOT EXISTS lp_kyc_parties (
    id               UUID PRIMARY KEY,
    tenant_id        UUID         NOT NULL,
    profile_id       UUID         NOT NULL REFERENCES lp_kyc_profiles(id),
    role             VARCHAR(20)  NOT NULL,
    name             VARCHAR(255) NOT NULL,
    id_number        VARCHAR(100),
    ownership_percent NUMERIC(5,2),
    owner_method     VARCHAR(10),
    pep_status       VARCHAR(5),
    verified_on      DATE,
    verified_by      UUID,
    verified_by_name VARCHAR(255),
    note             TEXT,
    CONSTRAINT ck_lp_kyp_role   CHECK (role IN ('BENEFICIAL_OWNER','AUTHORISED_PERSON','TRUSTEE','FOUNDER','BENEFICIARY','PARTNER')),
    CONSTRAINT ck_lp_kyp_pct    CHECK (ownership_percent IS NULL OR (ownership_percent > 0 AND ownership_percent <= 100)),
    CONSTRAINT ck_lp_kyp_method CHECK (owner_method IS NULL OR owner_method IN ('OWNERSHIP','CONTROL','MANAGEMENT')),
    CONSTRAINT ck_lp_kyp_pep    CHECK (pep_status IS NULL OR pep_status IN ('NONE','DPEP','PIP','FPEP')),
    CONSTRAINT ck_lp_kyp_bo     CHECK (role <> 'BENEFICIAL_OWNER' OR owner_method IS NOT NULL),
    CONSTRAINT ck_lp_kyp_ver    CHECK ((verified_on IS NULL) = (verified_by IS NULL))
);
CREATE INDEX IF NOT EXISTS idx_lp_kyp_profile ON lp_kyc_parties (tenant_id, profile_id);

CREATE TABLE IF NOT EXISTS lp_kyc_items (
    id               UUID PRIMARY KEY,
    tenant_id        UUID         NOT NULL,
    profile_id       UUID         NOT NULL REFERENCES lp_kyc_profiles(id),
    kind             VARCHAR(20)  NOT NULL,
    party_id         UUID REFERENCES lp_kyc_parties(id),
    description      VARCHAR(500) NOT NULL,
    document_ref     VARCHAR(255),
    evidence_id      UUID,
    verified_on      DATE         NOT NULL,
    verified_by      UUID         NOT NULL,
    verified_by_name VARCHAR(255),
    CONSTRAINT ck_lp_kyi_kind CHECK (kind IN ('IDENTITY','ADDRESS','REGISTRATION','OWNERSHIP_STRUCTURE','TRUST_DEED','OTHER'))
);
CREATE INDEX IF NOT EXISTS idx_lp_kyi_profile ON lp_kyc_items (tenant_id, profile_id);

CREATE TABLE IF NOT EXISTS lp_kyc_setting_changes (
    id              UUID PRIMARY KEY,
    tenant_id       UUID        NOT NULL,
    enforce         BOOLEAN     NOT NULL,
    reason          TEXT        NOT NULL,
    changed_by      UUID        NOT NULL,
    changed_by_name VARCHAR(255),
    changed_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_lp_kysc_reason CHECK (length(btrim(reason)) >= 10)
);
CREATE INDEX IF NOT EXISTS idx_lp_kysc_tenant ON lp_kyc_setting_changes (tenant_id, changed_at DESC);

CREATE OR REPLACE FUNCTION lp_kyc_guard() RETURNS trigger AS $$
DECLARE parent_status text;
BEGIN
    IF TG_TABLE_NAME = 'lp_kyc_profiles' THEN
        IF TG_OP = 'DELETE' THEN RAISE EXCEPTION 'due diligence profiles are never deleted' USING ERRCODE = 'integrity_constraint_violation'; END IF;
        -- A verified profile is a record: it never changes.
        IF OLD.status = 'VERIFIED' THEN RAISE EXCEPTION 'a verified profile cannot be changed; open a review instead' USING ERRCODE = 'integrity_constraint_violation'; END IF;
        IF NEW.id <> OLD.id OR NEW.tenant_id <> OLD.tenant_id OR NEW.client_id <> OLD.client_id OR NEW.started_at <> OLD.started_at THEN
            RAISE EXCEPTION 'a profile cannot be moved to another client' USING ERRCODE = 'integrity_constraint_violation'; END IF;
        RETURN NEW;
    END IF;
    -- parties and items follow their profile: only while it is a draft, and items are never rewritten
    SELECT status INTO parent_status FROM lp_kyc_profiles WHERE id = COALESCE(NEW.profile_id, OLD.profile_id);
    IF parent_status IS DISTINCT FROM 'DRAFT' THEN
        RAISE EXCEPTION '% can only change while the profile is a draft', TG_TABLE_NAME USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    IF TG_OP = 'UPDATE' AND TG_TABLE_NAME = 'lp_kyc_items' THEN
        RAISE EXCEPTION 'lp_kyc_items rows are not rewritten; remove and add again' USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_lp_kyc_profiles_guard ON lp_kyc_profiles;
CREATE TRIGGER trg_lp_kyc_profiles_guard BEFORE UPDATE OR DELETE ON lp_kyc_profiles FOR EACH ROW EXECUTE FUNCTION lp_kyc_guard();
DROP TRIGGER IF EXISTS trg_lp_kyc_parties_guard ON lp_kyc_parties;
CREATE TRIGGER trg_lp_kyc_parties_guard BEFORE INSERT OR UPDATE OR DELETE ON lp_kyc_parties FOR EACH ROW EXECUTE FUNCTION lp_kyc_guard();
DROP TRIGGER IF EXISTS trg_lp_kyc_items_guard ON lp_kyc_items;
CREATE TRIGGER trg_lp_kyc_items_guard BEFORE INSERT OR UPDATE OR DELETE ON lp_kyc_items FOR EACH ROW EXECUTE FUNCTION lp_kyc_guard();

CREATE OR REPLACE FUNCTION lp_kyc_setting_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'lp_kyc_setting_changes is append-only: % is not allowed', TG_OP USING ERRCODE = 'integrity_constraint_violation';
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_lp_kysc_append_only ON lp_kyc_setting_changes;
CREATE TRIGGER trg_lp_kysc_append_only BEFORE UPDATE OR DELETE ON lp_kyc_setting_changes FOR EACH ROW EXECUTE FUNCTION lp_kyc_setting_append_only();

-- Permissions -------------------------------------------------------------------------------------------------------
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'LEGALPRACTICE_KYC_PREPARE',        'Prepare and submit a client''s due diligence'),
    (gen_random_uuid(), 'LEGALPRACTICE_KYC_APPROVE',        'Approve a client''s due diligence (a second person)'),
    (gen_random_uuid(), 'LEGALPRACTICE_KYC_APPROVE_SENIOR', 'Approve due diligence for a high-risk or politically exposed client (senior management)'),
    (gen_random_uuid(), 'LEGALPRACTICE_KYC_SETTINGS',       'Decide whether matters and trust receipts are blocked until a client is verified')
ON CONFLICT (name) DO NOTHING;

UPDATE permissions SET module_key = 'legalpractice', resource = 'kyc',
       verb = CASE name WHEN 'LEGALPRACTICE_KYC_PREPARE' THEN 'PREPARE' WHEN 'LEGALPRACTICE_KYC_APPROVE' THEN 'APPROVE'
                        WHEN 'LEGALPRACTICE_KYC_APPROVE_SENIOR' THEN 'APPROVE_SENIOR' ELSE 'SETTINGS' END,
       sensitivity = CASE name WHEN 'LEGALPRACTICE_KYC_PREPARE' THEN 'NORMAL' ELSE 'SENSITIVE' END,
       requires_second_person = (name IN ('LEGALPRACTICE_KYC_APPROVE', 'LEGALPRACTICE_KYC_APPROVE_SENIOR')),
       grantable_by = 'ADMIN'
 WHERE name IN ('LEGALPRACTICE_KYC_PREPARE', 'LEGALPRACTICE_KYC_APPROVE', 'LEGALPRACTICE_KYC_APPROVE_SENIOR', 'LEGALPRACTICE_KYC_SETTINGS');

-- Whoever could open a matter (MANAGE or ADMIN) can prepare; ADMIN holders get the rest. Narrow them to the right people in Roles.
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.id
  FROM role_permissions rp
  JOIN permissions src ON src.id = rp.permission_id AND src.name IN ('LEGALPRACTICE_MANAGE', 'LEGALPRACTICE_ADMIN')
  JOIN permissions np ON np.name = 'LEGALPRACTICE_KYC_PREPARE'
 WHERE NOT EXISTS (SELECT 1 FROM role_permissions x WHERE x.role_id = rp.role_id AND x.permission_id = np.id);

INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, np.id
  FROM role_permissions rp
  JOIN permissions src ON src.id = rp.permission_id AND src.name = 'LEGALPRACTICE_ADMIN'
  JOIN permissions np ON np.name IN ('LEGALPRACTICE_KYC_APPROVE', 'LEGALPRACTICE_KYC_APPROVE_SENIOR', 'LEGALPRACTICE_KYC_SETTINGS')
 WHERE NOT EXISTS (SELECT 1 FROM role_permissions x WHERE x.role_id = rp.role_id AND x.permission_id = np.id);
