-- ============================================================================
-- Module 4b: Training Provider (outsourced accredited training academy)
--
-- VERSION NUMBER NOT CONFIRMED — follows directly from V259 (Module 4a,
-- Training internal) but has not been checked against the real
-- flyway_schema_history table. READ BEFORE APPLYING, and renumber if
-- another migration has landed on V260 since this was written.
-- ============================================================================

CREATE TABLE trainprov_profiles (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    trading_name            VARCHAR(200) NOT NULL,
    registration_number     VARCHAR(50),
    accreditation_body      VARCHAR(200),
    accreditation_number    VARCHAR(100),
    accreditation_expiry    DATE,
    address                 TEXT,
    phone                   VARCHAR(30),
    email                   VARCHAR(200),
    logo_url                TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_trainprov_profile_tenant UNIQUE (tenant_id)
);

CREATE TABLE trainprov_clients (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    client_code             VARCHAR(30) NOT NULL,
    trading_name            VARCHAR(200) NOT NULL,
    registration_number     VARCHAR(50),
    contact_name            VARCHAR(200),
    contact_email           VARCHAR(200),
    contact_phone           VARCHAR(30),
    address                 TEXT,
    status                  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at              TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_trainprov_client_code UNIQUE (tenant_id, client_code)
);
CREATE INDEX idx_trainprov_clients_tenant ON trainprov_clients (tenant_id) WHERE deleted_at IS NULL;

CREATE TABLE trainprov_courses (
    id                              UUID PRIMARY KEY,
    tenant_id                       UUID NOT NULL,
    course_code                     VARCHAR(30) NOT NULL,
    title                           VARCHAR(200) NOT NULL,
    description                     TEXT,
    unit_standard_number            VARCHAR(50),
    nqf_level                       INTEGER,
    credits                         INTEGER,
    duration_days                   NUMERIC(5,2),
    price_per_delegate              NUMERIC(15,2) NOT NULL CHECK (price_per_delegate >= 0),
    certification_offered           BOOLEAN NOT NULL DEFAULT FALSE,
    certificate_validity_months     INTEGER,
    status                          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at                      TIMESTAMPTZ,
    version                         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_trainprov_course_code UNIQUE (tenant_id, course_code)
);
CREATE INDEX idx_trainprov_courses_tenant ON trainprov_courses (tenant_id) WHERE deleted_at IS NULL;

CREATE TABLE trainprov_sessions (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    course_id       UUID NOT NULL REFERENCES trainprov_courses(id),
    session_type    VARCHAR(10) NOT NULL CHECK (session_type IN ('PUBLIC', 'CLOSED')),
    client_id       UUID REFERENCES trainprov_clients(id),
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
    CONSTRAINT ck_trainprov_session_dates CHECK (end_date >= start_date),
    -- Mirrors the entity-layer rule in TrainProvSession.create(): a
    -- CLOSED session must name exactly one client; a PUBLIC session
    -- must not.
    CONSTRAINT ck_trainprov_session_client_matches_type CHECK (
        (session_type = 'CLOSED' AND client_id IS NOT NULL) OR
        (session_type = 'PUBLIC' AND client_id IS NULL)
    )
);
CREATE INDEX idx_trainprov_sessions_tenant ON trainprov_sessions (tenant_id);
CREATE INDEX idx_trainprov_sessions_course ON trainprov_sessions (course_id);
CREATE INDEX idx_trainprov_sessions_client ON trainprov_sessions (client_id);
CREATE INDEX idx_trainprov_sessions_status_start ON trainprov_sessions (status, start_date);

CREATE TABLE trainprov_delegates (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    client_id           UUID NOT NULL REFERENCES trainprov_clients(id),
    delegate_number     VARCHAR(30) NOT NULL,
    full_name           VARCHAR(200) NOT NULL,
    id_number           VARCHAR(20),
    email               VARCHAR(200),
    phone               VARCHAR(30),
    job_title           VARCHAR(200),
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_trainprov_delegate_number UNIQUE (tenant_id, client_id, delegate_number)
);
CREATE INDEX idx_trainprov_delegates_tenant ON trainprov_delegates (tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_trainprov_delegates_client ON trainprov_delegates (client_id);

CREATE TABLE trainprov_enrollments (
    id                          UUID PRIMARY KEY,
    tenant_id                   UUID NOT NULL,
    session_id                  UUID NOT NULL REFERENCES trainprov_sessions(id),
    delegate_id                 UUID NOT NULL REFERENCES trainprov_delegates(id),
    client_id                   UUID NOT NULL REFERENCES trainprov_clients(id),
    delegate_name_snapshot      VARCHAR(200),
    status                      VARCHAR(20) NOT NULL DEFAULT 'ENROLLED'
                                    CHECK (status IN ('ENROLLED', 'ATTENDED', 'NO_SHOW', 'CANCELLED', 'COMPLETED', 'FAILED')),
    enrolled_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at                TIMESTAMPTZ,
    score                       NUMERIC(6,2),
    passed                      BOOLEAN,
    notes                       TEXT,
    cancel_reason               TEXT,
    invoiced                    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                     BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_trainprov_enrollments_tenant ON trainprov_enrollments (tenant_id);
CREATE INDEX idx_trainprov_enrollments_session ON trainprov_enrollments (session_id);
CREATE INDEX idx_trainprov_enrollments_client ON trainprov_enrollments (client_id);
CREATE INDEX idx_trainprov_enrollments_delegate ON trainprov_enrollments (delegate_id);
CREATE INDEX idx_trainprov_enrollments_billable ON trainprov_enrollments (client_id, invoiced) WHERE status <> 'CANCELLED';
CREATE UNIQUE INDEX uq_trainprov_enrollment_live
    ON trainprov_enrollments (session_id, delegate_id)
    WHERE status <> 'CANCELLED';

CREATE TABLE trainprov_certificates (
    id                          UUID PRIMARY KEY,
    tenant_id                   UUID NOT NULL,
    enrollment_id                UUID NOT NULL REFERENCES trainprov_enrollments(id),
    delegate_id                  UUID NOT NULL REFERENCES trainprov_delegates(id),
    client_id                    UUID NOT NULL REFERENCES trainprov_clients(id),
    delegate_name_snapshot       VARCHAR(200),
    client_name_snapshot         VARCHAR(200),
    course_title_snapshot        VARCHAR(200),
    unit_standard_snapshot       VARCHAR(50),
    certificate_number           VARCHAR(30) NOT NULL,
    issue_date                   DATE NOT NULL,
    expiry_date                  DATE,
    status                       VARCHAR(20) NOT NULL DEFAULT 'VALID' CHECK (status IN ('VALID', 'EXPIRED', 'REVOKED')),
    revoked_reason                TEXT,
    revoked_at                    TIMESTAMPTZ,
    created_at                    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                    TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                       BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_trainprov_certificate_enrollment UNIQUE (enrollment_id),
    CONSTRAINT uq_trainprov_certificate_number UNIQUE (tenant_id, certificate_number)
);
CREATE INDEX idx_trainprov_certificates_tenant ON trainprov_certificates (tenant_id);
CREATE INDEX idx_trainprov_certificates_client ON trainprov_certificates (client_id);
CREATE INDEX idx_trainprov_certificates_status_expiry ON trainprov_certificates (status, expiry_date);

CREATE TABLE trainprov_invoices (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    client_id         UUID NOT NULL REFERENCES trainprov_clients(id),
    invoice_number    VARCHAR(30) NOT NULL,
    period_start      DATE NOT NULL,
    period_end        DATE NOT NULL,
    issue_date        DATE NOT NULL,
    due_date          DATE NOT NULL,
    delegate_count    INTEGER NOT NULL DEFAULT 0,
    subtotal          NUMERIC(15,2) NOT NULL,
    vat_amount        NUMERIC(15,2) NOT NULL DEFAULT 0,
    total             NUMERIC(15,2) NOT NULL,
    amount_paid       NUMERIC(15,2) NOT NULL DEFAULT 0,
    status            VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'SENT', 'PARTIAL', 'PAID')),
    paid_at           TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_trainprov_invoice_number UNIQUE (tenant_id, invoice_number)
);
CREATE INDEX idx_trainprov_invoices_tenant ON trainprov_invoices (tenant_id);
CREATE INDEX idx_trainprov_invoices_client ON trainprov_invoices (client_id);
CREATE INDEX idx_trainprov_invoices_status_due ON trainprov_invoices (status, due_date);

CREATE TABLE trainprov_portal_access_grants (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    client_id           UUID NOT NULL REFERENCES trainprov_clients(id),
    invite_email        VARCHAR(200) NOT NULL,
    invite_token        VARCHAR(100) NOT NULL,
    invite_expires_at   TIMESTAMPTZ NOT NULL,
    portal_user_id      UUID,
    accepted_at         TIMESTAMPTZ,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED')),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_trainprov_portal_grant_token UNIQUE (invite_token)
);
CREATE INDEX idx_trainprov_portal_grants_client ON trainprov_portal_access_grants (client_id);

-- ── Module catalogue + permissions ──────────────────────────────────────────

INSERT INTO module_catalogue (key, name, description, monthly_price, icon, category, sort_order, is_active)
VALUES ('trainingprovider', 'Training Provider', 'Run an accredited training academy: course catalogue, scheduled public/in-house sessions, delegate management, certification and per-client billing', 449.00, 'award', 'Professional Services', 42, true)
ON CONFLICT (key) DO NOTHING;

INSERT INTO permissions (id, name, description)
VALUES
    (gen_random_uuid(), 'TRAININGPROVIDER_READ', 'View clients, courses, sessions, delegates, enrollments, certificates and invoices'),
    (gen_random_uuid(), 'TRAININGPROVIDER_MANAGE', 'Manage clients, courses, sessions, delegates and enrollments'),
    (gen_random_uuid(), 'TRAININGPROVIDER_ADMIN', 'Issue/revoke certificates, generate invoices, record payments, delete clients/courses/delegates')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
AND p.name IN ('TRAININGPROVIDER_READ', 'TRAININGPROVIDER_MANAGE', 'TRAININGPROVIDER_ADMIN')
AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
);
