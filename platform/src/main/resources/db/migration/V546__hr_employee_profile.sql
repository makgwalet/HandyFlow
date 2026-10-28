-- HR employee profile: next of kin and dependants, and a career timeline.
-- Relatives are kept (not deleted) when removed, so a beneficiary list can be shown as it stood. Career events are written by the
-- system when a title, department, manager, employment type or salary changes, and by HR for earlier employers and promotions.

CREATE TABLE IF NOT EXISTS hr_employee_relatives (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL REFERENCES tenants(id),
    employee_id         UUID         NOT NULL REFERENCES hr_employees(id),
    kind                VARCHAR(16)  NOT NULL CHECK (kind IN ('NEXT_OF_KIN','DEPENDANT')),
    full_name           VARCHAR(120) NOT NULL,
    relationship        VARCHAR(16)  NOT NULL,
    phone               VARCHAR(30),
    email               VARCHAR(150),
    date_of_birth       DATE,
    address             VARCHAR(300),
    beneficiary         BOOLEAN      NOT NULL DEFAULT FALSE,
    beneficiary_percent NUMERIC(5,2) CHECK (beneficiary_percent IS NULL OR (beneficiary_percent >= 0 AND beneficiary_percent <= 100)),
    notes               VARCHAR(300),
    created_by          UUID,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    removed_at          TIMESTAMP,
    removed_by          UUID
);
CREATE INDEX IF NOT EXISTS idx_hr_relatives_employee ON hr_employee_relatives (tenant_id, employee_id) WHERE removed_at IS NULL;

CREATE TABLE IF NOT EXISTS hr_career_events (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID         NOT NULL REFERENCES tenants(id),
    employee_id  UUID         NOT NULL REFERENCES hr_employees(id),
    kind         VARCHAR(30)  NOT NULL,
    event_date   DATE         NOT NULL,
    end_date     DATE,
    employer     VARCHAR(150),
    job_title    VARCHAR(120),
    department   VARCHAR(100),
    from_value   VARCHAR(200),
    to_value     VARCHAR(200),
    note         VARCHAR(500),
    pay_related  BOOLEAN      NOT NULL DEFAULT FALSE,
    auto         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by   UUID,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    removed_at   TIMESTAMP,
    removed_by   UUID,
    CHECK (end_date IS NULL OR end_date >= event_date)
);
CREATE INDEX IF NOT EXISTS idx_hr_career_employee ON hr_career_events (tenant_id, employee_id, event_date DESC) WHERE removed_at IS NULL;

-- People already on the books get a starting point, and an end point if they have left.
INSERT INTO hr_career_events (tenant_id, employee_id, kind, event_date, job_title, department, to_value, note, pay_related, auto)
SELECT e.tenant_id, e.id, 'HIRED', e.start_date, e.job_title, e.department, e.job_title, NULL, FALSE, TRUE
FROM hr_employees e
WHERE NOT EXISTS (SELECT 1 FROM hr_career_events c WHERE c.employee_id = e.id AND c.kind = 'HIRED');

INSERT INTO hr_career_events (tenant_id, employee_id, kind, event_date, note, pay_related, auto)
SELECT e.tenant_id, e.id, 'TERMINATED', e.end_date, LEFT(e.termination_reason, 500), FALSE, TRUE
FROM hr_employees e
WHERE e.status = 'TERMINATED' AND e.end_date IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM hr_career_events c WHERE c.employee_id = e.id AND c.kind = 'TERMINATED');
