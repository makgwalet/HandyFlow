-- HR Slice 10: performance.
--
--   * Review cycles. HR opens a cycle for a period; every employee in scope gets a review. The employee writes a self-review, the manager
--     rates, HR may adjust the final rating (with a reason), then releases the result to the employee, who acknowledges and may reply.
--     Once released, the ratings and comments cannot change.
--   * Goals, set by the manager or proposed by the employee and approved, with progress notes and an outcome.
--   * 360 feedback. Colleagues are asked for feedback on an employee in a cycle. The employee and the manager see a summary without names,
--     and only once at least three people have replied. HR can see who said what.
--   * Improvement plans. A plan states the shortfall, the standard, the support given and the time allowed; it is told to the employee,
--     checked in on, and closed met / not met. A plan can belong to an incapacity (poor performance) case.
--
-- Permissions (given to roles that already hold HR_MANAGE):
--   HR_PERF_READ    see every review, goal and plan
--   HR_PERF_MANAGE  run cycles, adjust ratings, release results, start and close improvement plans
-- Managers and employees need no permission to work on their own reviews, goals and their team's, which follow the reporting lines.

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'HR_PERF_READ',   'See all performance reviews, goals and improvement plans'),
    (gen_random_uuid(), 'HR_PERF_MANAGE', 'Run performance cycles, release results and manage improvement plans')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, p.id
FROM role_permissions rp
JOIN permissions held ON held.id = rp.permission_id AND held.name = 'HR_MANAGE'
CROSS JOIN permissions p
WHERE p.name IN ('HR_PERF_READ', 'HR_PERF_MANAGE')
ON CONFLICT DO NOTHING;

CREATE TABLE hr_perf_cycles (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID         NOT NULL REFERENCES tenants(id),
    name          VARCHAR(120) NOT NULL,
    period_start  DATE         NOT NULL,
    period_end    DATE         NOT NULL,
    self_due      DATE         NOT NULL,
    manager_due   DATE         NOT NULL,
    include_360   BOOLEAN      NOT NULL DEFAULT FALSE,
    status        VARCHAR(8)   NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'OPEN', 'CLOSED')),
    launched_at   TIMESTAMPTZ,
    launched_by   UUID,
    closed_at     TIMESTAMPTZ,
    closed_by     UUID,
    created_by    UUID,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version       BIGINT       NOT NULL DEFAULT 0,
    CHECK (period_end >= period_start),
    CHECK (manager_due >= self_due)
);
CREATE INDEX hr_perf_cycles_tenant ON hr_perf_cycles (tenant_id, period_start DESC);

CREATE TABLE hr_perf_reviews (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL REFERENCES tenants(id),
    cycle_id            UUID         NOT NULL REFERENCES hr_perf_cycles(id),
    employee_id         UUID         NOT NULL REFERENCES hr_employees(id),
    reviewer_id         UUID         REFERENCES hr_employees(id),
    job_title           VARCHAR(120),
    status              VARCHAR(16)  NOT NULL DEFAULT 'AWAITING_SELF'
                        CHECK (status IN ('AWAITING_SELF', 'AWAITING_MANAGER', 'SUBMITTED', 'SHARED', 'ACKNOWLEDGED', 'EXCLUDED')),
    self_rating         SMALLINT     CHECK (self_rating BETWEEN 1 AND 5),
    self_comment        TEXT,
    self_at             TIMESTAMPTZ,
    mgr_rating          SMALLINT     CHECK (mgr_rating BETWEEN 1 AND 5),
    mgr_comment         TEXT,
    mgr_at              TIMESTAMPTZ,
    mgr_by              UUID,
    final_rating        SMALLINT     CHECK (final_rating BETWEEN 1 AND 5),
    calibrated          BOOLEAN      NOT NULL DEFAULT FALSE,
    calibration_reason  VARCHAR(1000),
    shared_at           TIMESTAMPTZ,
    shared_by           UUID,
    ack_at              TIMESTAMPTZ,
    ack_by              UUID,
    employee_comment    TEXT,
    excluded_reason     VARCHAR(500),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version             BIGINT       NOT NULL DEFAULT 0,
    UNIQUE (cycle_id, employee_id)
);
CREATE INDEX hr_perf_reviews_employee ON hr_perf_reviews (tenant_id, employee_id);
CREATE INDEX hr_perf_reviews_reviewer ON hr_perf_reviews (tenant_id, reviewer_id);

-- Once the result is released, what the employee was shown cannot change. Only the acknowledgement can be added.
CREATE FUNCTION hr_perf_reviews_guard() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'A review cannot be deleted.';
    END IF;
    IF OLD.status IN ('SHARED', 'ACKNOWLEDGED') THEN
        IF NEW.employee_id IS DISTINCT FROM OLD.employee_id OR NEW.reviewer_id IS DISTINCT FROM OLD.reviewer_id
           OR NEW.self_rating IS DISTINCT FROM OLD.self_rating OR NEW.self_comment IS DISTINCT FROM OLD.self_comment
           OR NEW.mgr_rating IS DISTINCT FROM OLD.mgr_rating OR NEW.mgr_comment IS DISTINCT FROM OLD.mgr_comment
           OR NEW.final_rating IS DISTINCT FROM OLD.final_rating OR NEW.shared_at IS DISTINCT FROM OLD.shared_at THEN
            RAISE EXCEPTION 'A released review cannot be changed.';
        END IF;
        IF OLD.ack_at IS NOT NULL AND NEW.ack_at IS DISTINCT FROM OLD.ack_at THEN
            RAISE EXCEPTION 'The acknowledgement cannot be changed.';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER hr_perf_reviews_guard_trg BEFORE UPDATE OR DELETE ON hr_perf_reviews
    FOR EACH ROW EXECUTE FUNCTION hr_perf_reviews_guard();

CREATE TABLE hr_perf_goals (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID         NOT NULL REFERENCES tenants(id),
    employee_id   UUID         NOT NULL REFERENCES hr_employees(id),
    cycle_id      UUID         REFERENCES hr_perf_cycles(id),
    title         VARCHAR(200) NOT NULL,
    description   TEXT,
    measure       VARCHAR(500),
    due_date      DATE         NOT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'PROPOSED'
                  CHECK (status IN ('PROPOSED', 'ACTIVE', 'ACHIEVED', 'PARTLY_ACHIEVED', 'NOT_ACHIEVED', 'CANCELLED')),
    progress      SMALLINT     NOT NULL DEFAULT 0 CHECK (progress BETWEEN 0 AND 100),
    set_by        UUID,
    approved_by   UUID,
    approved_at   TIMESTAMPTZ,
    closed_by     UUID,
    closed_at     TIMESTAMPTZ,
    outcome_note  VARCHAR(2000),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version       BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX hr_perf_goals_employee ON hr_perf_goals (tenant_id, employee_id, due_date);

CREATE TABLE hr_perf_feedback (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID         NOT NULL REFERENCES tenants(id),
    review_id      UUID         NOT NULL REFERENCES hr_perf_reviews(id),
    provider_id    UUID         NOT NULL REFERENCES hr_employees(id),
    relationship   VARCHAR(14)  NOT NULL CHECK (relationship IN ('PEER', 'DIRECT_REPORT', 'OTHER')),
    status         VARCHAR(10)  NOT NULL DEFAULT 'REQUESTED' CHECK (status IN ('REQUESTED', 'SUBMITTED', 'DECLINED')),
    rating         SMALLINT     CHECK (rating BETWEEN 1 AND 5),
    strengths      TEXT,
    improvements   TEXT,
    decline_reason VARCHAR(500),
    requested_by   UUID,
    requested_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    responded_at   TIMESTAMPTZ,
    version        BIGINT       NOT NULL DEFAULT 0,
    UNIQUE (review_id, provider_id)
);
CREATE INDEX hr_perf_feedback_provider ON hr_perf_feedback (tenant_id, provider_id, status);

CREATE TABLE hr_perf_plans (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID         NOT NULL REFERENCES tenants(id),
    employee_id    UUID         NOT NULL REFERENCES hr_employees(id),
    owner_id       UUID         REFERENCES hr_employees(id),
    case_id        UUID         REFERENCES hr_er_cases(id),
    review_id      UUID         REFERENCES hr_perf_reviews(id),
    title          VARCHAR(200) NOT NULL,
    shortfall      TEXT         NOT NULL,
    standard       TEXT         NOT NULL,
    support        TEXT         NOT NULL,
    start_date     DATE         NOT NULL,
    end_date       DATE         NOT NULL,
    status         VARCHAR(10)  NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'ACTIVE', 'MET', 'NOT_MET', 'CANCELLED')),
    communicated_on DATE,
    closed_on      DATE,
    outcome_note   VARCHAR(2000),
    ack_at         TIMESTAMPTZ,
    ack_by         UUID,
    employee_comment TEXT,
    created_by     UUID,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version        BIGINT       NOT NULL DEFAULT 0,
    CHECK (end_date >= start_date),
    CHECK (status = 'DRAFT' OR status = 'CANCELLED' OR communicated_on IS NOT NULL)
);
CREATE INDEX hr_perf_plans_employee ON hr_perf_plans (tenant_id, employee_id);
CREATE INDEX hr_perf_plans_case ON hr_perf_plans (case_id) WHERE case_id IS NOT NULL;

-- A plan the employee has been told about keeps its shortfall, standard and support. The time allowed may be lengthened, never shortened.
CREATE FUNCTION hr_perf_plans_guard() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.status <> 'DRAFT' THEN RAISE EXCEPTION 'An improvement plan cannot be deleted. Cancel it instead.'; END IF;
        RETURN OLD;
    END IF;
    IF OLD.communicated_on IS NOT NULL THEN
        IF NEW.employee_id IS DISTINCT FROM OLD.employee_id OR NEW.shortfall IS DISTINCT FROM OLD.shortfall
           OR NEW.standard IS DISTINCT FROM OLD.standard OR NEW.support IS DISTINCT FROM OLD.support
           OR NEW.start_date IS DISTINCT FROM OLD.start_date OR NEW.communicated_on IS DISTINCT FROM OLD.communicated_on THEN
            RAISE EXCEPTION 'An improvement plan the employee has been told about cannot be rewritten.';
        END IF;
        IF NEW.end_date < OLD.end_date THEN
            RAISE EXCEPTION 'The time allowed in an improvement plan can be lengthened but not shortened.';
        END IF;
    END IF;
    IF OLD.status IN ('MET', 'NOT_MET', 'CANCELLED') THEN
        IF NEW.status IS DISTINCT FROM OLD.status OR NEW.end_date IS DISTINCT FROM OLD.end_date OR NEW.outcome_note IS DISTINCT FROM OLD.outcome_note THEN
            RAISE EXCEPTION 'A closed improvement plan cannot be changed.';
        END IF;
    END IF;
    IF OLD.ack_at IS NOT NULL AND NEW.ack_at IS DISTINCT FROM OLD.ack_at THEN
        RAISE EXCEPTION 'The acknowledgement cannot be changed.';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER hr_perf_plans_guard_trg BEFORE UPDATE OR DELETE ON hr_perf_plans
    FOR EACH ROW EXECUTE FUNCTION hr_perf_plans_guard();

CREATE TABLE hr_perf_checkins (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  UUID         NOT NULL REFERENCES tenants(id),
    plan_id    UUID         NOT NULL REFERENCES hr_perf_plans(id),
    on_date    DATE         NOT NULL,
    progress   VARCHAR(10)  NOT NULL CHECK (progress IN ('ON_TRACK', 'AT_RISK', 'OFF_TRACK')),
    note       TEXT         NOT NULL,
    done_by    UUID,
    done_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX hr_perf_checkins_plan ON hr_perf_checkins (plan_id, on_date);

CREATE TABLE hr_perf_events (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  UUID         NOT NULL REFERENCES tenants(id),
    kind       VARCHAR(8)   NOT NULL CHECK (kind IN ('CYCLE', 'REVIEW', 'GOAL', 'PLAN')),
    ref_id     UUID         NOT NULL,
    action     VARCHAR(30)  NOT NULL,
    note       VARCHAR(2000),
    done_by    UUID,
    done_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX hr_perf_events_ref ON hr_perf_events (kind, ref_id, done_at);

CREATE FUNCTION hr_perf_history_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'The performance history cannot be changed or deleted.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER hr_perf_events_no_update BEFORE UPDATE OR DELETE ON hr_perf_events
    FOR EACH ROW EXECUTE FUNCTION hr_perf_history_immutable();
CREATE TRIGGER hr_perf_checkins_no_update BEFORE UPDATE OR DELETE ON hr_perf_checkins
    FOR EACH ROW EXECUTE FUNCTION hr_perf_history_immutable();
