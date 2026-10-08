-- Clinic tasks (patch 0151): small follow-ups a clinician or the front desk must not lose, for example "call Mrs Dlamini
-- about her result" or "check the glucose again on Friday". Tasks are completed or dismissed with a note, never deleted.

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'CLINIC_TASK_READ', 'See clinic tasks (follow-ups, calls, results to chase)'),
    (gen_random_uuid(), 'CLINIC_TASK_CREATE', 'Create a clinic task for yourself or a colleague'),
    (gen_random_uuid(), 'CLINIC_TASK_COMPLETE', 'Complete or dismiss a clinic task')
ON CONFLICT (name) DO NOTHING;

-- Everyone who could read or write in the clinic before keeps being able to use tasks.
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.id
FROM role_permissions rp
JOIN permissions op ON op.id = rp.permission_id
JOIN (VALUES
    ('CLINIC_READ','CLINIC_TASK_READ'),
    ('CLINIC_WRITE','CLINIC_TASK_CREATE'),
    ('CLINIC_WRITE','CLINIC_TASK_COMPLETE')
) AS m(legacy, fine) ON m.legacy = op.name
JOIN permissions np ON np.name = m.fine
ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS clinic_tasks (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL REFERENCES tenants(id),
    patient_id      UUID         REFERENCES clinic_patients(id),
    assigned_to     UUID,
    kind            VARCHAR(20)  NOT NULL DEFAULT 'GENERAL' CHECK (kind IN ('GENERAL','RESULT_FOLLOW_UP','CALL_PATIENT','RECALL')),
    title           VARCHAR(200) NOT NULL,
    detail          VARCHAR(1000),
    due_date        DATE,
    status          VARCHAR(10)  NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','DONE','DISMISSED')),
    source_type     VARCHAR(30),
    source_id       UUID,
    created_by      UUID,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    closed_by       UUID,
    closed_at       TIMESTAMP,
    closing_note    VARCHAR(500),
    CHECK ((status = 'OPEN') = (closed_at IS NULL))
);
CREATE INDEX IF NOT EXISTS idx_clinic_tasks_open ON clinic_tasks(tenant_id, status, due_date);
CREATE INDEX IF NOT EXISTS idx_clinic_tasks_assignee ON clinic_tasks(tenant_id, assigned_to) WHERE status = 'OPEN';
-- One open task per source (for example one follow-up per lab result).
CREATE UNIQUE INDEX IF NOT EXISTS uq_clinic_tasks_open_source ON clinic_tasks(tenant_id, source_type, source_id, kind)
    WHERE status = 'OPEN' AND source_id IS NOT NULL;
