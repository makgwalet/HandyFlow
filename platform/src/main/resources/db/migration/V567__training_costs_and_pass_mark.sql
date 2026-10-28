-- Training: what a session actually cost, a pass mark on a course, and the permission to see spend and reports.
ALTER TABLE training_courses ADD COLUMN IF NOT EXISTS pass_mark NUMERIC(5,2)
    CHECK (pass_mark IS NULL OR (pass_mark >= 0 AND pass_mark <= 100));

CREATE TABLE IF NOT EXISTS training_session_costs (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    session_id  UUID NOT NULL REFERENCES training_sessions(id) ON DELETE CASCADE,
    category    VARCHAR(20) NOT NULL CHECK (category IN ('TRAINER_FEE', 'VENUE', 'MATERIALS', 'TRAVEL', 'CERTIFICATION_FEE', 'OTHER')),
    amount      NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    incurred_on DATE NOT NULL,
    note        VARCHAR(200),
    created_by  UUID,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_training_costs_session ON training_session_costs (tenant_id, session_id);
CREATE INDEX IF NOT EXISTS idx_training_costs_date ON training_session_costs (tenant_id, incurred_on);

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'TRAINING_REPORTS', 'See Training reports, session costs and spend')
ON CONFLICT (name) DO NOTHING;

-- Nothing changes on day one: every role that could read Training can read its reports.
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.id
FROM role_permissions rp
JOIN permissions src ON src.id = rp.permission_id AND src.name = 'TRAINING_READ'
JOIN permissions np ON np.name = 'TRAINING_REPORTS'
WHERE NOT EXISTS (SELECT 1 FROM role_permissions x WHERE x.role_id = rp.role_id AND x.permission_id = np.id);
