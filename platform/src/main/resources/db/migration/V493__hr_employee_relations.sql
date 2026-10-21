-- HR slice 8: employee relations cases (discipline, grievances, incapacity).
--
-- The old hr_disciplinary table becomes the case table, keeping every row and id, so the Security complaint links that store
-- hr_disciplinary_id keep working. What changes:
--   * The check on incident_type is dropped. It allowed five values, while the screen sent FINAL_WRITTEN_WARNING, NOTICE_TO_ATTEND and
--     OTHER and the Security referral sent free text such as "Absenteeism". Both were rejected by the database.
--   * incident_type is replaced by title (what the case is about) and outcome (what was decided). Old rows are copied across and marked
--     legacy: they were records of a sanction already given, so they become closed cases with that sanction as the outcome.
--   * Charges, steps (for incapacity), an immutable timeline and company policy settings live in their own tables.
--
-- Permissions (given to roles that already hold HR_MANAGE; HR_ER_READ also to roles that hold HR_READ):
--   HR_ER_READ    see cases
--   HR_ER_MANAGE  open and run cases, issue notices, record hearings, set steps
--   HR_ER_DECIDE  make findings, give sanctions, decide grievances and appeals
-- Confidential cases (ill health, harassment and similar, or flagged by HR) also need HR_SENSITIVE_READ unless the person owns the case.

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'HR_ER_READ',   'See employee relations cases'),
    (gen_random_uuid(), 'HR_ER_MANAGE', 'Open and run employee relations cases'),
    (gen_random_uuid(), 'HR_ER_DECIDE', 'Make findings, give sanctions and decide grievances and appeals')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, p.id
FROM role_permissions rp
JOIN permissions held ON held.id = rp.permission_id AND held.name = 'HR_MANAGE'
CROSS JOIN permissions p
WHERE p.name IN ('HR_ER_READ', 'HR_ER_MANAGE', 'HR_ER_DECIDE')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, p.id
FROM role_permissions rp
JOIN permissions held ON held.id = rp.permission_id AND held.name = 'HR_READ'
CROSS JOIN permissions p
WHERE p.name = 'HR_ER_READ'
ON CONFLICT DO NOTHING;

-- ── The case table ────────────────────────────────────────────────────────────────────────────────────

ALTER TABLE hr_disciplinary RENAME TO hr_er_cases;

DO $$
DECLARE c record;
BEGIN
    FOR c IN SELECT conname FROM pg_constraint
             WHERE conrelid = 'hr_er_cases'::regclass AND contype = 'c' AND pg_get_constraintdef(oid) LIKE '%incident_type%'
    LOOP
        EXECUTE format('ALTER TABLE hr_er_cases DROP CONSTRAINT %I', c.conname);
    END LOOP;
END $$;

ALTER TABLE hr_er_cases
    ADD COLUMN case_number      VARCHAR(20),
    ADD COLUMN case_type        VARCHAR(12)  NOT NULL DEFAULT 'DISCIPLINARY',
    ADD COLUMN category         VARCHAR(40)  NOT NULL DEFAULT 'OTHER',
    ADD COLUMN title            VARCHAR(200),
    ADD COLUMN status           VARCHAR(16)  NOT NULL DEFAULT 'REPORTED',
    ADD COLUMN confidential     BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN legacy           BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN reported_on      DATE,
    ADD COLUMN owner_user_id    UUID,
    ADD COLUMN initiator_id     UUID,
    ADD COLUMN chair_id         UUID,
    ADD COLUMN respondent_id    UUID,
    ADD COLUMN notice_issued_at TIMESTAMPTZ,
    ADD COLUMN hearing_time     TIME,
    ADD COLUMN hearing_venue    VARCHAR(200),
    ADD COLUMN hearing_held_on  DATE,
    ADD COLUMN hearing_notes    TEXT,
    ADD COLUMN representative   VARCHAR(200),
    ADD COLUMN employee_attended BOOLEAN,
    ADD COLUMN employee_response TEXT,
    ADD COLUMN decided_on       DATE,
    ADD COLUMN decision_note    TEXT,
    ADD COLUMN decided_by       UUID,
    ADD COLUMN warning_months   INT,
    ADD COLUMN warning_valid_until DATE,
    ADD COLUMN communicated_on  DATE,
    ADD COLUMN dismissal_rights_explained_on DATE,
    ADD COLUMN suspended_from   DATE,
    ADD COLUMN suspended_to     DATE,
    ADD COLUMN suspension_reason TEXT,
    ADD COLUMN review_from      DATE,
    ADD COLUMN review_to        DATE,
    ADD COLUMN appeal_lodged_on DATE,
    ADD COLUMN appeal_grounds   TEXT,
    ADD COLUMN appeal_decider_id UUID,
    ADD COLUMN appeal_decided_on DATE,
    ADD COLUMN appeal_outcome   VARCHAR(12),
    ADD COLUMN appeal_note      TEXT,
    ADD COLUMN closed_on        DATE,
    ADD COLUMN closed_reason    VARCHAR(500),
    ADD COLUMN version          BIGINT       NOT NULL DEFAULT 0;

-- Old rows were records of a sanction already given.
UPDATE hr_er_cases SET
    title       = INITCAP(REPLACE(incident_type, '_', ' ')),
    reported_on = incident_date,
    status      = 'CLOSED',
    outcome     = COALESCE(NULLIF(outcome, ''), CASE incident_type WHEN 'FINAL_WARNING' THEN 'FINAL_WRITTEN_WARNING' ELSE incident_type END),
    decided_on  = COALESCE(hearing_date, incident_date),
    closed_on   = COALESCE(hearing_date, incident_date),
    closed_reason = 'Imported from the earlier disciplinary record',
    legacy      = TRUE;

CREATE TABLE hr_er_counters (
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    year      INT  NOT NULL,
    last_no   INT  NOT NULL,
    PRIMARY KEY (tenant_id, year)
);

WITH numbered AS (
    SELECT id, EXTRACT(YEAR FROM created_at)::int AS yr,
           ROW_NUMBER() OVER (PARTITION BY tenant_id, EXTRACT(YEAR FROM created_at) ORDER BY created_at, id) AS rn
    FROM hr_er_cases
)
UPDATE hr_er_cases c SET case_number = 'ER-' || n.yr || '-' || LPAD(n.rn::text, 4, '0')
FROM numbered n WHERE c.id = n.id;

INSERT INTO hr_er_counters (tenant_id, year, last_no)
SELECT tenant_id, EXTRACT(YEAR FROM created_at)::int, COUNT(*) FROM hr_er_cases GROUP BY tenant_id, EXTRACT(YEAR FROM created_at);

ALTER TABLE hr_er_cases
    ALTER COLUMN case_number SET NOT NULL,
    ALTER COLUMN title SET NOT NULL,
    ALTER COLUMN reported_on SET NOT NULL,
    ALTER COLUMN incident_date DROP NOT NULL,
    DROP COLUMN incident_type;

ALTER TABLE hr_er_cases
    ADD CONSTRAINT hr_er_cases_number_unique UNIQUE (tenant_id, case_number),
    ADD CONSTRAINT hr_er_cases_type_check CHECK (case_type IN ('DISCIPLINARY', 'GRIEVANCE', 'INCAPACITY')),
    ADD CONSTRAINT hr_er_cases_status_check CHECK (status IN
        ('REPORTED', 'INVESTIGATING', 'NOTICE_ISSUED', 'HEARING_HELD', 'DECIDED', 'APPEALED', 'CLOSED', 'WITHDRAWN')),
    ADD CONSTRAINT hr_er_cases_appeal_check CHECK (appeal_outcome IS NULL OR appeal_outcome IN ('CONFIRMED', 'VARIED', 'OVERTURNED')),
    ADD CONSTRAINT hr_er_cases_warning_check CHECK (warning_months IS NULL OR warning_months BETWEEN 1 AND 60),
    ADD CONSTRAINT hr_er_cases_suspension_check CHECK (suspended_to IS NULL OR suspended_from IS NULL OR suspended_to >= suspended_from);

CREATE INDEX hr_er_cases_tenant_status ON hr_er_cases (tenant_id, status);
CREATE INDEX hr_er_cases_employee ON hr_er_cases (employee_id);

COMMENT ON COLUMN hr_er_cases.issued_by IS 'User who opened the case. (Column kept from the old disciplinary table.)';
COMMENT ON COLUMN hr_er_cases.initiator_id IS 'Employee who brings the charges or complaint (discipline). The chair may not be this person.';
COMMENT ON COLUMN hr_er_cases.chair_id IS 'Employee who chairs the hearing or decides the case. Never the employee concerned or the initiator.';
COMMENT ON COLUMN hr_er_cases.respondent_id IS 'Employee a grievance is about, when it is about a person.';
COMMENT ON COLUMN hr_er_cases.outcome IS 'Discipline: sanction or NOT_GUILTY. Grievance: UPHELD, PARTLY_UPHELD or NOT_UPHELD. Incapacity: the decision.';
COMMENT ON COLUMN hr_er_cases.suspension_reason IS 'Precautionary suspension is always on full pay. Suspension without pay as a sanction is not offered.';

-- ── Charges ──────────────────────────────────────────────────────────────────────────────────────────

CREATE TABLE hr_er_charges (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID         NOT NULL REFERENCES tenants(id),
    case_id      UUID         NOT NULL REFERENCES hr_er_cases(id),
    seq          INT          NOT NULL,
    charge       VARCHAR(1000) NOT NULL,
    finding      VARCHAR(10)  CHECK (finding IN ('GUILTY', 'NOT_GUILTY')),
    finding_note VARCHAR(2000),
    UNIQUE (case_id, seq)
);

-- ── Steps (incapacity) ───────────────────────────────────────────────────────────────────────────────

CREATE TABLE hr_er_steps (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID         NOT NULL REFERENCES tenants(id),
    case_id   UUID         NOT NULL REFERENCES hr_er_cases(id),
    code      VARCHAR(40)  NOT NULL,
    label     VARCHAR(200) NOT NULL,
    seq       INT          NOT NULL,
    done_on   DATE,
    done_by   UUID,
    note      VARCHAR(1000),
    UNIQUE (case_id, code)
);

-- ── Timeline: cannot be changed or deleted ───────────────────────────────────────────────────────────

CREATE TABLE hr_er_events (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL REFERENCES tenants(id),
    case_id             UUID         NOT NULL REFERENCES hr_er_cases(id),
    action              VARCHAR(40)  NOT NULL,
    note                VARCHAR(2000),
    visible_to_employee BOOLEAN      NOT NULL DEFAULT FALSE,
    done_by             UUID,
    done_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX hr_er_events_case ON hr_er_events (case_id, done_at);

CREATE FUNCTION hr_er_events_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'The case timeline cannot be changed or deleted.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER hr_er_events_no_update BEFORE UPDATE OR DELETE ON hr_er_events
    FOR EACH ROW EXECUTE FUNCTION hr_er_events_immutable();

-- ── Company policy: values the law does not fix ──────────────────────────────────────────────────────
-- The Code of Good Practice: Dismissal asks for reasonable time to prepare and a fair process but gives no fixed hours, warning lifespan
-- or appeal period, and there is no private-sector grievance deadline. These are the company's own settings, started at common practice.

CREATE TABLE hr_er_policy (
    tenant_id               UUID PRIMARY KEY REFERENCES tenants(id),
    notice_hours            INT NOT NULL DEFAULT 48 CHECK (notice_hours BETWEEN 1 AND 720),
    verbal_months           INT NOT NULL DEFAULT 3  CHECK (verbal_months BETWEEN 1 AND 60),
    written_months          INT NOT NULL DEFAULT 6  CHECK (written_months BETWEEN 1 AND 60),
    final_months            INT NOT NULL DEFAULT 12 CHECK (final_months BETWEEN 1 AND 60),
    appeal_days             INT NOT NULL DEFAULT 7  CHECK (appeal_days BETWEEN 1 AND 90),
    grievance_response_days INT NOT NULL DEFAULT 5  CHECK (grievance_response_days BETWEEN 1 AND 60),
    min_review_days         INT NOT NULL DEFAULT 28 CHECK (min_review_days BETWEEN 1 AND 365),
    updated_by              UUID,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
