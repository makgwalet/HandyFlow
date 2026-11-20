-- V823: Legal Practice — conflict-of-interest checks before a matter is opened. Confirm the next free Flyway version
-- (V822 is LP chunk 2).
--
-- A check searches the client and the other parties against the firm's clients and the parties on earlier matters.
-- It is the firm's record that the search was done, so checks, their subjects and their hits are never deleted, and a
-- matter's parties are never changed. A matter can only be opened from a usable check (enforced in the service); the
-- unique index below makes "one check opens one matter" true even under concurrent requests.
-- Matters that already exist have no check; their clients and any later-added parties are still searched.

CREATE TABLE IF NOT EXISTS lp_conflict_checks (
    id               UUID PRIMARY KEY,
    tenant_id        UUID         NOT NULL,
    client_id        UUID         NOT NULL,
    client_name      VARCHAR(255),
    status           VARCHAR(24)  NOT NULL,
    truncated        BOOLEAN      NOT NULL DEFAULT FALSE,
    run_by           UUID         NOT NULL,
    run_by_name      VARCHAR(255),
    run_at           TIMESTAMPTZ  NOT NULL,
    decided_by       UUID,
    decided_by_name  VARCHAR(255),
    decided_at       TIMESTAMPTZ,
    decision_reason  TEXT,
    self_reviewed    BOOLEAN      NOT NULL DEFAULT FALSE,
    valid_until      TIMESTAMPTZ,
    matter_id        UUID,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_lp_cc_status CHECK (status IN ('CLEAR','PENDING_REVIEW','CLEARED_AFTER_REVIEW','PROCEED_WITH_CONSENT','DECLINED')),
    -- A reviewed outcome always has a reviewer and a written reason.
    CONSTRAINT ck_lp_cc_decided CHECK (status IN ('CLEAR','PENDING_REVIEW')
                                       OR (decided_by IS NOT NULL AND decided_at IS NOT NULL AND length(btrim(coalesce(decision_reason,''))) >= 10)),
    -- The reviewer is a different person, unless the review was recorded as a self-review.
    CONSTRAINT ck_lp_cc_segregation CHECK (decided_by IS NULL OR decided_by <> run_by OR self_reviewed),
    -- Only checks that allow the firm to act have a validity window, and only they can have opened a matter.
    CONSTRAINT ck_lp_cc_matter CHECK (matter_id IS NULL OR status IN ('CLEAR','CLEARED_AFTER_REVIEW','PROCEED_WITH_CONSENT'))
);

CREATE INDEX IF NOT EXISTS idx_lp_cc_tenant_status ON lp_conflict_checks (tenant_id, status, run_at DESC);
CREATE INDEX IF NOT EXISTS idx_lp_cc_client        ON lp_conflict_checks (tenant_id, client_id, run_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uq_lp_cc_matter  ON lp_conflict_checks (matter_id) WHERE matter_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS lp_conflict_check_subjects (
    id         UUID PRIMARY KEY,
    tenant_id  UUID         NOT NULL,
    check_id   UUID         NOT NULL REFERENCES lp_conflict_checks(id),
    name       VARCHAR(255) NOT NULL,
    id_number  VARCHAR(100),
    role       VARCHAR(20)  NOT NULL,
    CONSTRAINT ck_lp_ccs_role CHECK (role IN ('CLIENT','OPPOSING_PARTY','CO_PARTY','RELATED_PARTY','WITNESS','OTHER'))
);
CREATE INDEX IF NOT EXISTS idx_lp_ccs_check ON lp_conflict_check_subjects (tenant_id, check_id);

CREATE TABLE IF NOT EXISTS lp_conflict_check_hits (
    id                UUID PRIMARY KEY,
    tenant_id         UUID         NOT NULL,
    check_id          UUID         NOT NULL REFERENCES lp_conflict_checks(id),
    subject_name      VARCHAR(255) NOT NULL,
    match_kind        VARCHAR(12)  NOT NULL,
    matched_kind      VARCHAR(14)  NOT NULL,
    matched_name      VARCHAR(255) NOT NULL,
    matched_role      VARCHAR(20),
    matched_detail    VARCHAR(500),
    matched_client_id UUID,
    matched_matter_id UUID,
    CONSTRAINT ck_lp_cch_match   CHECK (match_kind IN ('EXACT_ID','EXACT_NAME','SIMILAR')),
    CONSTRAINT ck_lp_cch_matched CHECK (matched_kind IN ('CLIENT','MATTER_PARTY'))
);
CREATE INDEX IF NOT EXISTS idx_lp_cch_check ON lp_conflict_check_hits (tenant_id, check_id);

CREATE TABLE IF NOT EXISTS lp_matter_parties (
    id         UUID PRIMARY KEY,
    tenant_id  UUID         NOT NULL,
    matter_id  UUID         NOT NULL,
    client_id  UUID         NOT NULL,
    name       VARCHAR(255) NOT NULL,
    id_number  VARCHAR(100),
    role       VARCHAR(20)  NOT NULL,
    added_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_lp_mp_role CHECK (role IN ('OPPOSING_PARTY','CO_PARTY','RELATED_PARTY','WITNESS','OTHER'))
);
CREATE INDEX IF NOT EXISTS idx_lp_mp_matter ON lp_matter_parties (tenant_id, matter_id);
CREATE INDEX IF NOT EXISTS idx_lp_mp_name   ON lp_matter_parties (tenant_id, lower(name));
CREATE INDEX IF NOT EXISTS idx_lp_mp_idnum  ON lp_matter_parties (tenant_id, lower(replace(replace(replace(id_number, ' ', ''), '-', ''), '/', ''))) WHERE id_number IS NOT NULL;
-- the search lower()s the client name and compares a cleaned ID number
CREATE INDEX IF NOT EXISTS idx_lp_clients_conflict_name ON lp_clients (tenant_id, lower(name));
CREATE INDEX IF NOT EXISTS idx_lp_clients_conflict_idnum ON lp_clients (tenant_id, lower(replace(replace(replace(id_or_registration_number, ' ', ''), '-', ''), '/', ''))) WHERE id_or_registration_number IS NOT NULL;

-- Never deleted or rewritten. conflict_checks may be updated (decision, matter link) but not deleted; the rest are append-only.
CREATE OR REPLACE FUNCTION lp_conflict_no_delete() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% rows are never deleted', TG_TABLE_NAME USING ERRCODE = 'integrity_constraint_violation';
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION lp_conflict_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% is append-only: % is not allowed', TG_TABLE_NAME, TG_OP USING ERRCODE = 'integrity_constraint_violation';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_lp_cc_no_delete ON lp_conflict_checks;
CREATE TRIGGER trg_lp_cc_no_delete BEFORE DELETE ON lp_conflict_checks FOR EACH ROW EXECUTE FUNCTION lp_conflict_no_delete();
DROP TRIGGER IF EXISTS trg_lp_ccs_append_only ON lp_conflict_check_subjects;
CREATE TRIGGER trg_lp_ccs_append_only BEFORE UPDATE OR DELETE ON lp_conflict_check_subjects FOR EACH ROW EXECUTE FUNCTION lp_conflict_append_only();
DROP TRIGGER IF EXISTS trg_lp_cch_append_only ON lp_conflict_check_hits;
CREATE TRIGGER trg_lp_cch_append_only BEFORE UPDATE OR DELETE ON lp_conflict_check_hits FOR EACH ROW EXECUTE FUNCTION lp_conflict_append_only();
DROP TRIGGER IF EXISTS trg_lp_mp_append_only ON lp_matter_parties;
CREATE TRIGGER trg_lp_mp_append_only BEFORE UPDATE OR DELETE ON lp_matter_parties FOR EACH ROW EXECUTE FUNCTION lp_conflict_append_only();

-- Permissions -------------------------------------------------------------------------------------------------------
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'LEGALPRACTICE_CONFLICT_RUN',    'Run a conflict-of-interest check before opening a matter'),
    (gen_random_uuid(), 'LEGALPRACTICE_CONFLICT_REVIEW', 'Decide a conflict check that found a possible conflict (a second person)')
ON CONFLICT (name) DO NOTHING;

UPDATE permissions SET module_key = 'legalpractice', resource = 'conflict',
       verb = CASE name WHEN 'LEGALPRACTICE_CONFLICT_RUN' THEN 'RUN' ELSE 'REVIEW' END,
       sensitivity = CASE name WHEN 'LEGALPRACTICE_CONFLICT_RUN' THEN 'NORMAL' ELSE 'SENSITIVE' END,
       requires_second_person = (name = 'LEGALPRACTICE_CONFLICT_REVIEW'),
       grantable_by = 'ADMIN'
 WHERE name IN ('LEGALPRACTICE_CONFLICT_RUN', 'LEGALPRACTICE_CONFLICT_REVIEW');

-- Whoever could open a matter (MANAGE or ADMIN) can run a check; whoever is ADMIN can also review. No access is lost or gained.
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.id
  FROM role_permissions rp
  JOIN permissions src ON src.id = rp.permission_id AND src.name IN ('LEGALPRACTICE_MANAGE', 'LEGALPRACTICE_ADMIN')
  JOIN permissions np ON np.name = 'LEGALPRACTICE_CONFLICT_RUN'
 WHERE NOT EXISTS (SELECT 1 FROM role_permissions x WHERE x.role_id = rp.role_id AND x.permission_id = np.id);

INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, np.id
  FROM role_permissions rp
  JOIN permissions src ON src.id = rp.permission_id AND src.name = 'LEGALPRACTICE_ADMIN'
  JOIN permissions np ON np.name = 'LEGALPRACTICE_CONFLICT_REVIEW'
 WHERE NOT EXISTS (SELECT 1 FROM role_permissions x WHERE x.role_id = rp.role_id AND x.permission_id = np.id);
