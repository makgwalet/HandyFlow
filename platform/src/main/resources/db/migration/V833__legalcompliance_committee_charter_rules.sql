-- ============================================================================
-- Legal / Compliance — committee charter rules. Confirm the next free Flyway version before applying (V832 is the last Legal Practice migration).
--
-- One row per version of a committee's charter rules. Rows are never changed or deleted: a change is a new row with the next version_no and a reason. The current
-- rules are the row with the highest version_no whose effective_from is not in the future. A committee with no row keeps the earlier house-rule behaviour.
-- ============================================================================

CREATE TABLE legalcompliance_committee_charter_rules (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    committee_id          UUID NOT NULL REFERENCES legalcompliance_committees (id),
    version_no            INTEGER NOT NULL,
    charter_reference     VARCHAR(120),
    adopted_on            DATE NOT NULL,
    effective_from        DATE NOT NULL,
    casting_vote          BOOLEAN NOT NULL,
    written_mode          VARCHAR(16) NOT NULL,
    quorum_after_recusal  BOOLEAN NOT NULL,
    special_percent       INTEGER NOT NULL,
    change_reason         TEXT NOT NULL,
    recorded_by_name      VARCHAR(255),
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL,
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lc_charter_version UNIQUE (committee_id, version_no),
    CONSTRAINT ck_lc_charter_written CHECK (written_mode IN ('NOT_ALLOWED','UNANIMOUS','MAJORITY_OF_ALL','EITHER')),
    CONSTRAINT ck_lc_charter_pct CHECK (special_percent BETWEEN 51 AND 100),
    CONSTRAINT ck_lc_charter_reason CHECK (length(trim(change_reason)) >= 10),
    CONSTRAINT ck_lc_charter_dates CHECK (effective_from >= adopted_on)
);

CREATE INDEX ix_lc_charter_committee ON legalcompliance_committee_charter_rules (tenant_id, committee_id, version_no DESC);

CREATE OR REPLACE FUNCTION lc_charter_rules_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Charter rules are append-only: record a new version instead';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_lc_charter_rules_no_change BEFORE UPDATE OR DELETE ON legalcompliance_committee_charter_rules
    FOR EACH ROW EXECUTE FUNCTION lc_charter_rules_append_only();

-- Permission: recording a charter changes how votes are counted, so it is SENSITIVE and held by ADMIN only (reading uses the existing read authorities).
INSERT INTO permissions (id, name, description, module_key, resource, verb, sensitivity, requires_second_person, grantable_by)
SELECT gen_random_uuid(), 'LEGALCOMPLIANCE_CHARTER_MANAGE', 'Record committee charter rules (casting vote, written resolutions, quorum, special resolution percentage)',
       'legalcompliance', 'charter', 'MANAGE', 'SENSITIVE', false, 'ADMIN'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'LEGALCOMPLIANCE_CHARTER_MANAGE');

UPDATE permissions SET module_key = 'legalcompliance', resource = 'charter', verb = 'MANAGE', sensitivity = 'SENSITIVE', requires_second_person = false, grantable_by = 'ADMIN'
WHERE name = 'LEGALCOMPLIANCE_CHARTER_MANAGE';

INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, np.id FROM role_permissions rp
JOIN permissions op ON op.id = rp.permission_id AND op.name = 'LEGALCOMPLIANCE_ADMIN'
JOIN permissions np ON np.name = 'LEGALCOMPLIANCE_CHARTER_MANAGE'
ON CONFLICT DO NOTHING;
