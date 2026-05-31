-- V40__recruiter_module.sql
-- Recruiter module — job postings, applicant tracking pipeline,
-- public careers page per tenant, CV storage, HR onboarding link.
--
-- WHY Option A (token-based, no applicant login)?
-- SME tenants get tens of applicants per role, not thousands.
-- Token-based flow is low friction and standard practice.
-- Applicant submits → gets email with token link → tracks via token.
-- Phase 2: shared careers.handyflow.co.za portal with applicant accounts.

-- ── Job postings ──────────────────────────────────────────────────────────────
CREATE TABLE rec_jobs (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    title           VARCHAR(255) NOT NULL,
    department      VARCHAR(100),
    location        VARCHAR(255),
    job_type        VARCHAR(20)  NOT NULL DEFAULT 'FULL_TIME'
        CHECK (job_type IN ('FULL_TIME','PART_TIME','CONTRACT','INTERNSHIP','FREELANCE')),
    experience_level VARCHAR(20) NOT NULL DEFAULT 'MID'
        CHECK (experience_level IN ('JUNIOR','MID','SENIOR','LEAD','EXECUTIVE')),
    description     TEXT        NOT NULL,
    requirements    TEXT,
    benefits        TEXT,
    salary_min      NUMERIC(12,2),
    salary_max      NUMERIC(12,2),
    salary_currency VARCHAR(3)  NOT NULL DEFAULT 'ZAR',
    show_salary     BOOLEAN     NOT NULL DEFAULT false,
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','OPEN','PAUSED','CLOSED','FILLED')),
    -- Public careers page
    -- Each tenant gets a public URL: /careers/{tenant-slug}/{job-slug}
    slug            VARCHAR(255),
    closes_at       DATE,
    -- Counts (denormalised for performance)
    application_count INT NOT NULL DEFAULT 0,
    created_by      UUID        REFERENCES users(id),
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP,

    CONSTRAINT pk_rec_jobs PRIMARY KEY (id),
    CONSTRAINT uq_rec_job_slug UNIQUE (tenant_id, slug)
);

CREATE INDEX idx_rec_jobs_tenant ON rec_jobs(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_rec_jobs_status ON rec_jobs(tenant_id, status) WHERE deleted_at IS NULL;

-- ── Applicants ────────────────────────────────────────────────────────────────
-- WHY separate applicants table? One person can apply to multiple jobs.
-- We deduplicate by email per tenant so their details stay consistent.
CREATE TABLE rec_applicants (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    email           VARCHAR(255) NOT NULL,
    phone           VARCHAR(30),
    location        VARCHAR(255),
    linkedin_url    VARCHAR(500),
    portfolio_url   VARCHAR(500),
    cv_url          TEXT,            -- base64 PDF
    cv_name         VARCHAR(255),
    -- Token for public portal access (no login needed)
    portal_token    VARCHAR(100) UNIQUE,
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_rec_applicants PRIMARY KEY (id),
    CONSTRAINT uq_rec_applicant_email UNIQUE (tenant_id, email)
);

CREATE INDEX idx_rec_applicants_tenant ON rec_applicants(tenant_id);
CREATE INDEX idx_rec_applicants_token  ON rec_applicants(portal_token);

-- ── Applications (one applicant → one job) ────────────────────────────────────
-- WHY separate table? Applicant can apply to multiple jobs at the same tenant.
-- Each application has its own pipeline stage and notes.
CREATE TABLE rec_applications (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    job_id          UUID        NOT NULL REFERENCES rec_jobs(id) ON DELETE CASCADE,
    applicant_id    UUID        NOT NULL REFERENCES rec_applicants(id),
    -- Pipeline stage
    stage           VARCHAR(30)  NOT NULL DEFAULT 'APPLIED'
        CHECK (stage IN (
            'APPLIED','SCREENING','INTERVIEW','ASSESSMENT',
            'OFFER','HIRED','REJECTED','WITHDRAWN'
        )),
    -- Source tracking
    source          VARCHAR(30)  DEFAULT 'CAREERS_PAGE'
        CHECK (source IN ('CAREERS_PAGE','REFERRAL','LINKEDIN','INDEED','MANUAL','OTHER')),
    -- Internal
    score           INT,                -- 1-5 star rating by recruiter
    notes           TEXT,               -- internal notes
    rejection_reason VARCHAR(255),
    -- HR link — set when applicant is converted to employee
    hr_employee_id  UUID,
    -- Timestamps
    applied_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    stage_changed_at TIMESTAMP  NOT NULL DEFAULT NOW(),
    hired_at        TIMESTAMP,
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_rec_applications PRIMARY KEY (id),
    CONSTRAINT uq_rec_application UNIQUE (job_id, applicant_id)
);

CREATE INDEX idx_rec_applications_tenant  ON rec_applications(tenant_id);
CREATE INDEX idx_rec_applications_job     ON rec_applications(job_id);
CREATE INDEX idx_rec_applications_stage   ON rec_applications(tenant_id, stage);
CREATE INDEX idx_rec_applications_applicant ON rec_applications(applicant_id);

-- ── Interview notes ───────────────────────────────────────────────────────────
CREATE TABLE rec_interviews (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    application_id  UUID        NOT NULL REFERENCES rec_applications(id) ON DELETE CASCADE,
    tenant_id       UUID        NOT NULL,
    interview_type  VARCHAR(20)  NOT NULL DEFAULT 'VIDEO'
        CHECK (interview_type IN ('PHONE','VIDEO','IN_PERSON','TECHNICAL','PANEL')),
    scheduled_at    TIMESTAMP,
    interviewer_id  UUID REFERENCES users(id),
    interviewer_name VARCHAR(255),
    outcome         VARCHAR(20)
        CHECK (outcome IN ('PENDING','PASSED','FAILED','NO_SHOW','RESCHEDULED')),
    notes           TEXT,
    score           INT,            -- 1-5
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_rec_interviews PRIMARY KEY (id)
);

CREATE INDEX idx_rec_interviews_application ON rec_interviews(application_id);

-- ── Pipeline stage history ────────────────────────────────────────────────────
-- WHY? Audit trail of stage changes — "moved to INTERVIEW on 2026-05-22 by Thabo"
CREATE TABLE rec_stage_history (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    application_id  UUID        NOT NULL REFERENCES rec_applications(id) ON DELETE CASCADE,
    from_stage      VARCHAR(30),
    to_stage        VARCHAR(30)  NOT NULL,
    changed_by_name VARCHAR(255),
    notes           TEXT,
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_rec_stage_history PRIMARY KEY (id)
);

CREATE INDEX idx_rec_stage_history_app ON rec_stage_history(application_id);

-- ── Module catalogue ──────────────────────────────────────────────────────────
INSERT INTO module_catalogue (key, name, description, monthly_price, icon, category, sort_order)
VALUES (
    'recruiter',
    'Recruiter',
    'Job postings, applicant tracking pipeline, public careers page, CV storage. Links to HR for onboarding hired candidates.',
    349.00, 'briefcase', 'OPERATIONS', 97
) ON CONFLICT (key) DO NOTHING;

INSERT INTO tenant_modules (tenant_id, module_key, status, trial_ends_at)
SELECT t.id, 'recruiter', 'TRIAL', NOW() + INTERVAL '60 days'
FROM tenants t WHERE t.slug = 'zeta-earthmoving'
ON CONFLICT (tenant_id, module_key) DO NOTHING;

-- ── Permissions ───────────────────────────────────────────────────────────────
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'RECRUITER_READ',   'View job postings and applications'),
    (gen_random_uuid(), 'RECRUITER_MANAGE', 'Create jobs, move pipeline stages, add notes'),
    (gen_random_uuid(), 'RECRUITER_ADMIN',  'Manage job postings and hiring decisions')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name IN ('RECRUITER_READ','RECRUITER_MANAGE','RECRUITER_ADMIN')
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
