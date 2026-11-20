-- V824: Legal Practice — deadline engine. Confirm the next free Flyway version (V823 is LP chunk 3).
--
-- A rule says how long a period is and how it is counted (court days, calendar days, months, years). A computation is the
-- firm's record of one due date worked out from a rule and a trigger date: it keeps a copy of the rule as it was, the days
-- that were skipped and why, and who did it. Computations are never changed or deleted; correcting a trigger date adds a
-- new computation that supersedes the old one. The key date on the matter is what the diary, the calendar and the reminder
-- job already read.

CREATE TABLE IF NOT EXISTS lp_deadline_rules (
    id               UUID PRIMARY KEY,
    tenant_id        UUID         NOT NULL,
    code             VARCHAR(60)  NOT NULL,
    name             VARCHAR(200) NOT NULL,
    description      TEXT,
    court            VARCHAR(40),
    trigger_label    VARCHAR(120) NOT NULL,
    key_date_type    VARCHAR(30)  NOT NULL,
    amount           INTEGER      NOT NULL,
    unit             VARCHAR(14)  NOT NULL,
    direction        VARCHAR(6)   NOT NULL,
    rollover         VARCHAR(26)  NOT NULL,
    recess_start     VARCHAR(5),
    recess_end       VARCHAR(5),
    source_reference VARCHAR(255),
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_lp_dr_unit      CHECK (unit IN ('COURT_DAYS','CALENDAR_DAYS','MONTHS','YEARS')),
    CONSTRAINT ck_lp_dr_direction CHECK (direction IN ('AFTER','BEFORE')),
    CONSTRAINT ck_lp_dr_rollover  CHECK (rollover IN ('NONE','SKIP_SUNDAY_AND_HOLIDAYS','SKIP_NON_COURT_DAYS')),
    CONSTRAINT ck_lp_dr_kdtype    CHECK (key_date_type IN ('COURT_DATE','PRESCRIPTION_DEADLINE','FILING_DEADLINE','LIMITATION_PERIOD','OTHER')),
    CONSTRAINT ck_lp_dr_amount    CHECK (amount >= 1 AND amount <= CASE unit WHEN 'MONTHS' THEN 360 WHEN 'YEARS' THEN 30 ELSE 3660 END),
    CONSTRAINT ck_lp_dr_recess    CHECK ((recess_start IS NULL) = (recess_end IS NULL)),
    CONSTRAINT ck_lp_dr_recess_fmt CHECK (recess_start IS NULL OR (recess_start ~ '^(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])$' AND recess_end ~ '^(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])$')),
    CONSTRAINT ck_lp_dr_code      CHECK (length(btrim(code)) >= 2)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_lp_dr_code ON lp_deadline_rules (tenant_id, lower(code));

-- Days the law or the court makes non-working that cannot be worked out: a proclaimed holiday, an election day, a flood.
CREATE TABLE IF NOT EXISTS lp_court_calendar_entries (
    id            UUID PRIMARY KEY,
    tenant_id     UUID         NOT NULL,
    entry_date    DATE         NOT NULL,
    kind          VARCHAR(14)  NOT NULL,
    reason        VARCHAR(255) NOT NULL,
    created_by    UUID,
    created_by_name VARCHAR(255),
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_lp_cce_kind   CHECK (kind IN ('PUBLIC_HOLIDAY','COURT_CLOSURE')),
    CONSTRAINT ck_lp_cce_reason CHECK (length(btrim(reason)) >= 3)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_lp_cce_day ON lp_court_calendar_entries (tenant_id, entry_date, kind);

CREATE TABLE IF NOT EXISTS lp_deadline_computations (
    id               UUID PRIMARY KEY,
    tenant_id        UUID         NOT NULL,
    matter_id        UUID         NOT NULL,
    key_date_id      UUID         NOT NULL,
    supersedes_id    UUID REFERENCES lp_deadline_computations(id),
    rule_id          UUID,
    rule_code        VARCHAR(60)  NOT NULL,
    rule_name        VARCHAR(200) NOT NULL,
    amount           INTEGER      NOT NULL,
    unit             VARCHAR(14)  NOT NULL,
    direction        VARCHAR(6)   NOT NULL,
    rollover         VARCHAR(26)  NOT NULL,
    recess_start     VARCHAR(5),
    recess_end       VARCHAR(5),
    trigger_label    VARCHAR(120) NOT NULL,
    trigger_date     DATE         NOT NULL,
    due_date         DATE         NOT NULL,
    explanation      TEXT         NOT NULL,
    reason           TEXT,
    computed_by      UUID         NOT NULL,
    computed_by_name VARCHAR(255),
    computed_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_lp_dc_unit      CHECK (unit IN ('COURT_DAYS','CALENDAR_DAYS','MONTHS','YEARS')),
    CONSTRAINT ck_lp_dc_direction CHECK (direction IN ('AFTER','BEFORE')),
    -- A correction always says why.
    CONSTRAINT ck_lp_dc_reason    CHECK (supersedes_id IS NULL OR length(btrim(coalesce(reason,''))) >= 10)
);
CREATE INDEX IF NOT EXISTS idx_lp_dc_matter ON lp_deadline_computations (tenant_id, matter_id, computed_at DESC);
CREATE INDEX IF NOT EXISTS idx_lp_dc_key    ON lp_deadline_computations (tenant_id, key_date_id, computed_at DESC);
-- One first computation per key date, and a computation is superseded at most once, so the history is a line, not a tree.
CREATE UNIQUE INDEX IF NOT EXISTS uq_lp_dc_first      ON lp_deadline_computations (key_date_id) WHERE supersedes_id IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_lp_dc_supersedes ON lp_deadline_computations (supersedes_id) WHERE supersedes_id IS NOT NULL;

CREATE OR REPLACE FUNCTION lp_deadline_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% is append-only: % is not allowed', TG_TABLE_NAME, TG_OP USING ERRCODE = 'integrity_constraint_violation';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_lp_dc_append_only ON lp_deadline_computations;
CREATE TRIGGER trg_lp_dc_append_only BEFORE UPDATE OR DELETE ON lp_deadline_computations FOR EACH ROW EXECUTE FUNCTION lp_deadline_append_only();

-- Permissions -------------------------------------------------------------------------------------------------------

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'LEGALPRACTICE_DEADLINE_COMPUTE', 'Work out a deadline on a matter from a rule and a trigger date, and correct it'),
    (gen_random_uuid(), 'LEGALPRACTICE_DEADLINE_RULES',   'Maintain the deadline rules and the firm''s court calendar (a wrong rule gives wrong dates on every matter)')
ON CONFLICT (name) DO NOTHING;

UPDATE permissions SET module_key = 'legalpractice',
       resource = CASE name WHEN 'LEGALPRACTICE_DEADLINE_COMPUTE' THEN 'deadline' ELSE 'deadline-rules' END,
       verb = CASE name WHEN 'LEGALPRACTICE_DEADLINE_COMPUTE' THEN 'COMPUTE' ELSE 'MANAGE' END,
       sensitivity = CASE name WHEN 'LEGALPRACTICE_DEADLINE_COMPUTE' THEN 'NORMAL' ELSE 'SENSITIVE' END,
       requires_second_person = FALSE,
       grantable_by = 'ADMIN'
 WHERE name IN ('LEGALPRACTICE_DEADLINE_COMPUTE', 'LEGALPRACTICE_DEADLINE_RULES');

-- Whoever could add a key date by hand (MANAGE or ADMIN) can compute one; only ADMIN holders keep the rules. No access is lost or gained.
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.id
  FROM role_permissions rp
  JOIN permissions src ON src.id = rp.permission_id AND src.name IN ('LEGALPRACTICE_MANAGE', 'LEGALPRACTICE_ADMIN')
  JOIN permissions np ON np.name = 'LEGALPRACTICE_DEADLINE_COMPUTE'
 WHERE NOT EXISTS (SELECT 1 FROM role_permissions x WHERE x.role_id = rp.role_id AND x.permission_id = np.id);

INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.id
  FROM role_permissions rp
  JOIN permissions src ON src.id = rp.permission_id AND src.name = 'LEGALPRACTICE_ADMIN'
  JOIN permissions np ON np.name = 'LEGALPRACTICE_DEADLINE_RULES'
 WHERE NOT EXISTS (SELECT 1 FROM role_permissions x WHERE x.role_id = rp.role_id AND x.permission_id = np.id);
