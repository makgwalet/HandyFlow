-- ============================================================================
-- Module 4a: Training / Learning & Development (internal)
--
-- VERSION NUMBER NOT CONFIRMED — this follows directly from V258
-- (warehousing) but has not been checked against the real
-- flyway_schema_history table. READ BEFORE APPLYING, and renumber if
-- another migration has landed on V259 since this was written.
-- ============================================================================

CREATE TABLE training_courses (
    id                             UUID PRIMARY KEY,
    tenant_id                      UUID NOT NULL,
    course_code                    VARCHAR(30) NOT NULL,
    title                          VARCHAR(200) NOT NULL,
    description                    TEXT,
    category                       VARCHAR(100),
    delivery_mode                  VARCHAR(20) NOT NULL DEFAULT 'IN_PERSON'
                                        CHECK (delivery_mode IN ('IN_PERSON', 'ONLINE', 'HYBRID')),
    duration_hours                 NUMERIC(6,2),
    default_trainer_name           VARCHAR(200),
    cost                           NUMERIC(15,2),
    certification_offered          BOOLEAN NOT NULL DEFAULT FALSE,
    certificate_validity_months    INTEGER,
    status                         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                                        CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    created_at                     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                     TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at                     TIMESTAMPTZ,
    version                        BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_training_course_code UNIQUE (tenant_id, course_code)
);
CREATE INDEX idx_training_courses_tenant ON training_courses (tenant_id) WHERE deleted_at IS NULL;

CREATE TABLE training_sessions (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    course_id       UUID NOT NULL REFERENCES training_courses(id),
    start_date      DATE NOT NULL,
    end_date        DATE NOT NULL,
    venue           VARCHAR(200),
    trainer_name    VARCHAR(200),
    capacity        INTEGER,
    status          VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED'
                        CHECK (status IN ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    notes           TEXT,
    cancel_reason   TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_training_session_dates CHECK (end_date >= start_date)
);
CREATE INDEX idx_training_sessions_tenant ON training_sessions (tenant_id);
CREATE INDEX idx_training_sessions_course ON training_sessions (course_id);
CREATE INDEX idx_training_sessions_status_start ON training_sessions (status, start_date);

CREATE TABLE training_enrollments (
    id                          UUID PRIMARY KEY,
    tenant_id                   UUID NOT NULL,
    session_id                  UUID NOT NULL REFERENCES training_sessions(id),
    employee_id                 UUID NOT NULL, -- hr.HrEmployee.id, by reference only (no FK across module boundary)
    employee_name_snapshot      VARCHAR(200),
    employee_number_snapshot    VARCHAR(50),
    status                      VARCHAR(20) NOT NULL DEFAULT 'ENROLLED'
                                    CHECK (status IN ('ENROLLED', 'ATTENDED', 'NO_SHOW', 'CANCELLED', 'COMPLETED', 'FAILED')),
    enrolled_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at                TIMESTAMPTZ,
    score                       NUMERIC(6,2),
    passed                      BOOLEAN,
    notes                       TEXT,
    cancel_reason               TEXT,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                     BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_training_enrollments_tenant ON training_enrollments (tenant_id);
CREATE INDEX idx_training_enrollments_session ON training_enrollments (session_id);
CREATE INDEX idx_training_enrollments_employee ON training_enrollments (employee_id);
-- Belt-and-braces mirror of TrainingEnrollmentRepository.findActiveEnrollment's
-- "no duplicate live enrollment for the same employee in the same session" rule.
-- Partial unique index (not a plain UNIQUE constraint) because CANCELLED
-- enrollments must NOT block a genuine re-enrollment.
CREATE UNIQUE INDEX uq_training_enrollment_live
    ON training_enrollments (session_id, employee_id)
    WHERE status <> 'CANCELLED';

CREATE TABLE training_certificates (
    id                        UUID PRIMARY KEY,
    tenant_id                 UUID NOT NULL,
    enrollment_id             UUID NOT NULL REFERENCES training_enrollments(id),
    employee_id               UUID NOT NULL,
    employee_name_snapshot    VARCHAR(200),
    course_title_snapshot     VARCHAR(200),
    certificate_number        VARCHAR(30) NOT NULL,
    issue_date                DATE NOT NULL,
    expiry_date               DATE,
    status                    VARCHAR(20) NOT NULL DEFAULT 'VALID'
                                   CHECK (status IN ('VALID', 'EXPIRED', 'REVOKED')),
    revoked_reason             TEXT,
    revoked_at                 TIMESTAMPTZ,
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                    BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_training_certificate_enrollment UNIQUE (enrollment_id),
    CONSTRAINT uq_training_certificate_number UNIQUE (tenant_id, certificate_number)
);
CREATE INDEX idx_training_certificates_tenant ON training_certificates (tenant_id);
CREATE INDEX idx_training_certificates_employee ON training_certificates (employee_id);
CREATE INDEX idx_training_certificates_status_expiry ON training_certificates (status, expiry_date);

-- ── Module catalogue + permissions ──────────────────────────────────────────

INSERT INTO module_catalogue (key, name, description, monthly_price, icon, category, sort_order, is_active)
VALUES ('training', 'Training & L&D', 'Course catalogue, scheduled sessions, enrollments, completions and certifications for your own employees', 249.00, 'graduation-cap', 'HR', 41, true)
ON CONFLICT (key) DO NOTHING;

INSERT INTO permissions (id, name, description)
VALUES
    (gen_random_uuid(), 'TRAINING_READ', 'View training courses, sessions, enrollments and certificates'),
    (gen_random_uuid(), 'TRAINING_MANAGE', 'Manage courses, sessions and enrollments'),
    (gen_random_uuid(), 'TRAINING_ADMIN', 'Issue/revoke certificates, delete courses and sessions')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
AND p.name IN ('TRAINING_READ', 'TRAINING_MANAGE', 'TRAINING_ADMIN')
AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
);
