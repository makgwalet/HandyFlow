-- V829: Legal Practice — litigation case tracking. Confirm the next free Flyway version (V828 is LP chunk 9).
--
-- One court case per matter: court, case number, the stage it has reached, the other side, and how it ended. Hearings feed the matter's
-- key-date calendar (key_date_id), as do the compliance dates of court orders. Cases, hearings and orders are never deleted (a hearing is
-- cancelled, a case is concluded); the event log is append-only.

CREATE TABLE IF NOT EXISTS lp_litigation_cases (
    id                          UUID PRIMARY KEY,
    tenant_id                   UUID         NOT NULL,
    matter_id                   UUID         NOT NULL,
    client_id                   UUID         NOT NULL,
    court_level                 VARCHAR(20)  NOT NULL,
    court_name                  VARCHAR(255) NOT NULL,
    case_number                 VARCHAR(100),
    our_role                    VARCHAR(12)  NOT NULL,
    stage                       VARCHAR(12)  NOT NULL DEFAULT 'PRE_ACTION',
    opposing_party              VARCHAR(255),
    opposing_attorney           VARCHAR(255),
    opposing_attorney_contact   VARCHAR(255),
    judge                       VARCHAR(255),
    issued_on                   DATE,
    served_on                   DATE,
    outcome                     VARCHAR(12),
    concluded_on                DATE,
    outcome_note                TEXT,
    notes                       TEXT,
    created_by                  UUID         NOT NULL,
    created_by_name             VARCHAR(255),
    created_at                  TIMESTAMPTZ  NOT NULL,
    updated_at                  TIMESTAMPTZ  NOT NULL,
    version                     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_lp_lit_matter UNIQUE (tenant_id, matter_id),
    CONSTRAINT ck_lp_lit_court   CHECK (court_level IN ('MAGISTRATES','HIGH_COURT','SCA','CONSTITUTIONAL','LABOUR','TAX','EQUALITY','OTHER')),
    CONSTRAINT ck_lp_lit_role    CHECK (our_role IN ('PLAINTIFF','DEFENDANT','APPLICANT','RESPONDENT','APPELLANT','OTHER')),
    CONSTRAINT ck_lp_lit_stage   CHECK (stage IN ('PRE_ACTION','PLEADINGS','DISCOVERY','PRE_TRIAL','TRIAL','JUDGMENT','APPEAL','EXECUTION','CONCLUDED')),
    CONSTRAINT ck_lp_lit_outcome CHECK (outcome IS NULL OR outcome IN ('WON','LOST','SETTLED','WITHDRAWN','STRUCK_OFF','OTHER')),
    CONSTRAINT ck_lp_lit_name    CHECK (length(btrim(court_name)) >= 2),
    -- a case is concluded exactly when it has an outcome and a date, and the outcome is explained
    CONSTRAINT ck_lp_lit_concluded CHECK ((stage = 'CONCLUDED') = (outcome IS NOT NULL AND concluded_on IS NOT NULL)
                                       AND (stage <> 'CONCLUDED' OR length(btrim(coalesce(outcome_note,''))) >= 10)),
    -- once proceedings are issued the case has a number
    CONSTRAINT ck_lp_lit_number  CHECK (stage = 'PRE_ACTION' OR length(btrim(coalesce(case_number,''))) >= 2),
    CONSTRAINT ck_lp_lit_dates   CHECK ((issued_on IS NULL OR served_on IS NULL OR served_on >= issued_on) AND (issued_on IS NULL OR concluded_on IS NULL OR concluded_on >= issued_on))
);
CREATE INDEX IF NOT EXISTS idx_lp_lit_stage ON lp_litigation_cases (tenant_id, stage);

CREATE TABLE IF NOT EXISTS lp_hearings (
    id             UUID PRIMARY KEY,
    tenant_id      UUID         NOT NULL,
    case_id        UUID         NOT NULL REFERENCES lp_litigation_cases(id),
    matter_id      UUID         NOT NULL,
    hearing_type   VARCHAR(20)  NOT NULL,
    hearing_date   DATE         NOT NULL,
    hearing_time   TIME,
    court_room     VARCHAR(100),
    appearing      VARCHAR(255),
    status         VARCHAR(10)  NOT NULL DEFAULT 'SCHEDULED',
    postponements  INTEGER      NOT NULL DEFAULT 0,
    outcome        TEXT,
    cancel_reason  TEXT,
    key_date_id    UUID,
    created_by     UUID         NOT NULL,
    created_by_name VARCHAR(255),
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_lp_hr_type   CHECK (hearing_type IN ('TRIAL','MOTION','APPLICATION','CASE_MANAGEMENT','PRE_TRIAL','APPEAL','JUDGMENT','OTHER')),
    CONSTRAINT ck_lp_hr_status CHECK (status IN ('SCHEDULED','HELD','CANCELLED')),
    CONSTRAINT ck_lp_hr_post   CHECK (postponements >= 0),
    CONSTRAINT ck_lp_hr_held   CHECK (status <> 'HELD' OR length(btrim(coalesce(outcome,''))) >= 10),
    CONSTRAINT ck_lp_hr_cancel CHECK (status <> 'CANCELLED' OR length(btrim(coalesce(cancel_reason,''))) >= 10)
);
CREATE INDEX IF NOT EXISTS idx_lp_hr_case ON lp_hearings (tenant_id, case_id, hearing_date);
CREATE INDEX IF NOT EXISTS idx_lp_hr_roll ON lp_hearings (tenant_id, hearing_date) WHERE status = 'SCHEDULED';

CREATE TABLE IF NOT EXISTS lp_court_orders (
    id               UUID PRIMARY KEY,
    tenant_id        UUID         NOT NULL,
    case_id          UUID         NOT NULL REFERENCES lp_litigation_cases(id),
    matter_id        UUID         NOT NULL,
    hearing_id       UUID REFERENCES lp_hearings(id),
    order_date       DATE         NOT NULL,
    summary          TEXT         NOT NULL,
    compliance_due   DATE,
    complied_on      DATE,
    complied_note    TEXT,
    key_date_id      UUID,
    created_by       UUID         NOT NULL,
    created_by_name  VARCHAR(255),
    created_at       TIMESTAMPTZ  NOT NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_lp_co_summary CHECK (length(btrim(summary)) >= 10),
    CONSTRAINT ck_lp_co_due     CHECK (compliance_due IS NULL OR compliance_due >= order_date),
    CONSTRAINT ck_lp_co_comp    CHECK (complied_on IS NULL OR (compliance_due IS NOT NULL AND complied_on >= order_date))
);
CREATE INDEX IF NOT EXISTS idx_lp_co_case ON lp_court_orders (tenant_id, case_id, order_date DESC);

CREATE TABLE IF NOT EXISTS lp_litigation_events (
    id           UUID PRIMARY KEY,
    tenant_id    UUID         NOT NULL,
    case_id      UUID         NOT NULL REFERENCES lp_litigation_cases(id),
    matter_id    UUID         NOT NULL,
    event_type   VARCHAR(20)  NOT NULL,
    actor_id     UUID         NOT NULL,
    actor_name   VARCHAR(255),
    detail       TEXT,
    occurred_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_lp_le_type CHECK (event_type IN ('CASE_OPENED','DETAILS_EDITED','STAGE_CHANGED','CASE_CONCLUDED','CASE_REOPENED','HEARING_SET','HEARING_MOVED','HEARING_HELD','HEARING_CANCELLED','ORDER_RECORDED','ORDER_COMPLIED'))
);
CREATE INDEX IF NOT EXISTS idx_lp_le_case ON lp_litigation_events (tenant_id, case_id, occurred_at DESC);

CREATE OR REPLACE FUNCTION lp_litigation_guard() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN RAISE EXCEPTION '% rows are never deleted', TG_TABLE_NAME USING ERRCODE = 'integrity_constraint_violation'; END IF;
    IF NEW.id <> OLD.id OR NEW.tenant_id <> OLD.tenant_id OR NEW.matter_id <> OLD.matter_id THEN
        RAISE EXCEPTION '% cannot be moved to another matter', TG_TABLE_NAME USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    -- one table at a time: a record only has the columns of its own table
    IF TG_TABLE_NAME = 'lp_court_orders' THEN
        IF NEW.case_id <> OLD.case_id OR NEW.order_date <> OLD.order_date OR NEW.summary <> OLD.summary OR NEW.compliance_due IS DISTINCT FROM OLD.compliance_due THEN
            RAISE EXCEPTION 'a recorded court order cannot be rewritten' USING ERRCODE = 'integrity_constraint_violation';
        END IF;
        IF OLD.complied_on IS NOT NULL AND NEW.complied_on IS DISTINCT FROM OLD.complied_on THEN
            RAISE EXCEPTION 'compliance with an order cannot be changed once recorded' USING ERRCODE = 'integrity_constraint_violation';
        END IF;
    ELSIF TG_TABLE_NAME = 'lp_hearings' THEN
        IF NEW.case_id <> OLD.case_id OR (OLD.status <> 'SCHEDULED' AND (NEW.status <> OLD.status OR NEW.hearing_date <> OLD.hearing_date)) THEN
            RAISE EXCEPTION 'a hearing that has been held or cancelled cannot be changed' USING ERRCODE = 'integrity_constraint_violation';
        END IF;
        IF NEW.postponements < OLD.postponements THEN
            RAISE EXCEPTION 'postponements cannot go down' USING ERRCODE = 'integrity_constraint_violation';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_lp_lit_guard ON lp_litigation_cases;
CREATE TRIGGER trg_lp_lit_guard BEFORE UPDATE OR DELETE ON lp_litigation_cases FOR EACH ROW EXECUTE FUNCTION lp_litigation_guard();
DROP TRIGGER IF EXISTS trg_lp_hr_guard ON lp_hearings;
CREATE TRIGGER trg_lp_hr_guard BEFORE UPDATE OR DELETE ON lp_hearings FOR EACH ROW EXECUTE FUNCTION lp_litigation_guard();
DROP TRIGGER IF EXISTS trg_lp_co_guard ON lp_court_orders;
CREATE TRIGGER trg_lp_co_guard BEFORE UPDATE OR DELETE ON lp_court_orders FOR EACH ROW EXECUTE FUNCTION lp_litigation_guard();

CREATE OR REPLACE FUNCTION lp_litigation_event_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'lp_litigation_events is append-only: % is not allowed', TG_OP USING ERRCODE = 'integrity_constraint_violation';
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_lp_le_append_only ON lp_litigation_events;
CREATE TRIGGER trg_lp_le_append_only BEFORE UPDATE OR DELETE ON lp_litigation_events FOR EACH ROW EXECUTE FUNCTION lp_litigation_event_append_only();

-- Permissions -------------------------------------------------------------------------------------------------------
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'LEGALPRACTICE_LITIGATION_MANAGE',   'Open and maintain a matter''s court case: details, stage, hearings, court orders'),
    (gen_random_uuid(), 'LEGALPRACTICE_LITIGATION_CONCLUDE', 'Conclude a court case with its outcome, and reopen one')
ON CONFLICT (name) DO NOTHING;

UPDATE permissions SET module_key = 'legalpractice', resource = 'litigation',
       verb = CASE name WHEN 'LEGALPRACTICE_LITIGATION_MANAGE' THEN 'MANAGE' ELSE 'CONCLUDE' END,
       sensitivity = CASE name WHEN 'LEGALPRACTICE_LITIGATION_MANAGE' THEN 'NORMAL' ELSE 'SENSITIVE' END,
       requires_second_person = FALSE, grantable_by = 'ADMIN'
 WHERE name IN ('LEGALPRACTICE_LITIGATION_MANAGE', 'LEGALPRACTICE_LITIGATION_CONCLUDE');

-- Whoever could manage matters (MANAGE or ADMIN) can maintain the case; ADMIN holders may conclude and reopen. Narrow in Roles.
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.id
  FROM role_permissions rp
  JOIN permissions src ON src.id = rp.permission_id AND src.name IN ('LEGALPRACTICE_MANAGE', 'LEGALPRACTICE_ADMIN')
  JOIN permissions np ON np.name = 'LEGALPRACTICE_LITIGATION_MANAGE'
 WHERE NOT EXISTS (SELECT 1 FROM role_permissions x WHERE x.role_id = rp.role_id AND x.permission_id = np.id);

INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, np.id
  FROM role_permissions rp
  JOIN permissions src ON src.id = rp.permission_id AND src.name = 'LEGALPRACTICE_ADMIN'
  JOIN permissions np ON np.name = 'LEGALPRACTICE_LITIGATION_CONCLUDE'
 WHERE NOT EXISTS (SELECT 1 FROM role_permissions x WHERE x.role_id = rp.role_id AND x.permission_id = np.id);
