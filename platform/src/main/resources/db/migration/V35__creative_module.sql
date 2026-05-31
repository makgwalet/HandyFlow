-- V35__creative_module.sql
-- Creative module — design briefs, proof uploads, client approvals, deliverables.
-- WHY a separate module? Creative agencies and freelancers need a structured
-- way to manage client projects from brief to final delivery.
-- The proof approval flow is the key differentiator — clients approve without login.

-- ── Jobs (project containers) ─────────────────────────────────────────────────
CREATE TABLE cre_jobs (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    customer_id     UUID        REFERENCES customers(id) ON DELETE SET NULL,
    client_name     VARCHAR(255) NOT NULL,
    client_email    VARCHAR(255),
    title           VARCHAR(255) NOT NULL,
    job_type        VARCHAR(50)  NOT NULL DEFAULT 'OTHER'
        CHECK (job_type IN (
            'LOGO','SOCIAL_MEDIA','VIDEO','PHOTOGRAPHY','PRINT',
            'WEB_DESIGN','ANIMATION','COPYWRITING','BRANDING',
            'ILLUSTRATION','PACKAGING','PRESENTATION','OTHER'
        )),
    description     TEXT,
    brief           TEXT,
    status          VARCHAR(30)  NOT NULL DEFAULT 'BRIEFING'
        CHECK (status IN (
            'BRIEFING','IN_PROGRESS','AWAITING_APPROVAL',
            'IN_REVISION','APPROVED','DELIVERED','INVOICED','CANCELLED'
        )),
    priority        VARCHAR(10)  NOT NULL DEFAULT 'NORMAL'
        CHECK (priority IN ('LOW','NORMAL','HIGH','URGENT')),
    due_date        DATE,
    budget          NUMERIC(12,2),
    quoted_amount   NUMERIC(12,2),
    invoice_id      UUID,
    notes           TEXT,
    assigned_to     UUID REFERENCES users(id),
    created_by      UUID REFERENCES users(id),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP,
    version         BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT pk_cre_jobs PRIMARY KEY (id)
);

CREATE INDEX idx_cre_jobs_tenant   ON cre_jobs(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_cre_jobs_status   ON cre_jobs(tenant_id, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_cre_jobs_customer ON cre_jobs(customer_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_cre_jobs_assigned ON cre_jobs(assigned_to) WHERE deleted_at IS NULL;

-- ── Proofs ────────────────────────────────────────────────────────────────────
-- WHY approval_token? Client approves without logging in.
-- A UUID token is emailed to the client. Public endpoint validates the token.
-- Token invalidated when: (1) new version uploaded, (2) client approves.
CREATE TABLE cre_proofs (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    job_id              UUID        NOT NULL REFERENCES cre_jobs(id) ON DELETE CASCADE,
    tenant_id           UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    version_number      INT         NOT NULL DEFAULT 1,
    title               VARCHAR(255),
    file_url            TEXT,
    file_name           VARCHAR(255),
    file_type           VARCHAR(50),
    thumbnail_url       TEXT,
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','APPROVED','REJECTED','SUPERSEDED')),
    approval_token      VARCHAR(100) NOT NULL UNIQUE,
    token_expires_at    TIMESTAMP    NOT NULL,
    sent_at             TIMESTAMP,
    sent_to_email       VARCHAR(255),
    approved_at         TIMESTAMP,
    approved_by_name    VARCHAR(255),
    approved_by_email   VARCHAR(255),
    approved_by_ip      VARCHAR(45),
    rejection_reason    TEXT,
    notes               TEXT,
    uploaded_by         UUID REFERENCES users(id),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_cre_proofs PRIMARY KEY (id),
    CONSTRAINT uq_cre_proof_version UNIQUE (job_id, version_number)
);

CREATE INDEX idx_cre_proofs_job   ON cre_proofs(job_id);
CREATE INDEX idx_cre_proofs_token ON cre_proofs(approval_token);

-- ── Proof comments ────────────────────────────────────────────────────────────
CREATE TABLE cre_proof_comments (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    proof_id    UUID        NOT NULL REFERENCES cre_proofs(id) ON DELETE CASCADE,
    tenant_id   UUID        NOT NULL,
    author_name VARCHAR(255) NOT NULL,
    author_type VARCHAR(10)  NOT NULL CHECK (author_type IN ('TEAM','CLIENT')),
    comment     TEXT        NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_cre_proof_comments PRIMARY KEY (id)
);

CREATE INDEX idx_cre_proof_comments_proof ON cre_proof_comments(proof_id);

-- ── Deliverables ──────────────────────────────────────────────────────────────
CREATE TABLE cre_deliverables (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    job_id      UUID        NOT NULL REFERENCES cre_jobs(id) ON DELETE CASCADE,
    tenant_id   UUID        NOT NULL,
    file_url    TEXT        NOT NULL,
    file_name   VARCHAR(255) NOT NULL,
    file_type   VARCHAR(50),
    file_size   BIGINT,
    notes       TEXT,
    uploaded_by UUID REFERENCES users(id),
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_cre_deliverables PRIMARY KEY (id)
);

CREATE INDEX idx_cre_deliverables_job ON cre_deliverables(job_id);

-- ── Module catalogue entry ────────────────────────────────────────────────────
INSERT INTO module_catalogue (key, name, description, monthly_price, icon, category, sort_order)
VALUES (
    'creative',
    'Creative Studio',
    'Design briefs, proof approvals, client sign-off portal, deliverable management. For designers, photographers and agencies.',
    349.00, 'palette', 'OPERATIONS', 90
) ON CONFLICT (key) DO NOTHING;

-- Add to zeta-earthmoving pilot
INSERT INTO tenant_modules (tenant_id, module_key, status, trial_ends_at)
SELECT t.id, 'creative', 'TRIAL', NOW() + INTERVAL '60 days'
FROM tenants t WHERE t.slug = 'zeta-earthmoving'
ON CONFLICT (tenant_id, module_key) DO NOTHING;

-- ── Permissions ───────────────────────────────────────────────────────────────
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'CREATIVE_READ',   'View creative jobs and proofs'),
    (gen_random_uuid(), 'CREATIVE_MANAGE', 'Create and manage jobs, upload proofs')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name IN ('CREATIVE_READ','CREATIVE_MANAGE')
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
