-- Patch 0573: named client logins for Projects (the shared portal_users login), what each can see, and what clients send in.

-- Access for one email: every project of a client, or only chosen projects. The invite link's token is stored as a SHA-256 hash.
CREATE TABLE IF NOT EXISTS project_portal_grants (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID          NOT NULL,
    client_id          UUID,                              -- projects.client_id when the projects carry one
    client_name        VARCHAR(255)  NOT NULL,            -- otherwise "all projects" matches projects.client_name (trimmed, any case)
    all_projects       BOOLEAN       NOT NULL DEFAULT true,
    portal_user_id     UUID,                              -- portal_users.id once accepted
    invite_email       VARCHAR(255)  NOT NULL,
    contact_name       VARCHAR(255),
    status             VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
    invite_token_hash  VARCHAR(64),
    invite_expires_at  TIMESTAMPTZ,
    invited_by         UUID,
    invited_by_name    VARCHAR(255),
    invited_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    accepted_at        TIMESTAMPTZ,
    last_seen_at       TIMESTAMPTZ,
    revoked_by         UUID,
    revoked_at         TIMESTAMPTZ,
    CONSTRAINT chk_ppg_status CHECK (status IN ('PENDING','ACTIVE','REVOKED')),
    CONSTRAINT chk_ppg_client CHECK (btrim(client_name) <> ''),
    CONSTRAINT chk_ppg_user   CHECK (status <> 'ACTIVE' OR portal_user_id IS NOT NULL)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_ppg_invite_token ON project_portal_grants (invite_token_hash) WHERE invite_token_hash IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_ppg_user   ON project_portal_grants (portal_user_id) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_ppg_tenant ON project_portal_grants (tenant_id, status);
-- one live grant per email per client within a tenant (widen or narrow its projects instead of adding a second)
CREATE UNIQUE INDEX IF NOT EXISTS uq_ppg_email_client ON project_portal_grants (tenant_id, LOWER(invite_email), COALESCE(client_id::text, LOWER(btrim(client_name))))
    WHERE status IN ('PENDING','ACTIVE');

CREATE TABLE IF NOT EXISTS project_portal_grant_projects (
    grant_id    UUID NOT NULL REFERENCES project_portal_grants(id) ON DELETE CASCADE,
    project_id  UUID NOT NULL REFERENCES projects(id),
    PRIMARY KEY (grant_id, project_id)
);

-- Questions, change requests and new-project requests from clients. Staff accept (turning them into an RFI, a draft
-- change order or a project in planning), answer, or decline with a reason.
CREATE TABLE IF NOT EXISTS project_portal_requests (
    id               UUID PRIMARY KEY,
    tenant_id        UUID          NOT NULL,
    project_id       UUID          REFERENCES projects(id),           -- NULL for a new-project request
    grant_id         UUID          NOT NULL REFERENCES project_portal_grants(id),
    request_number   VARCHAR(20)   NOT NULL,
    kind             VARCHAR(12)   NOT NULL,
    title            VARCHAR(300)  NOT NULL,
    description      TEXT,
    needed_by        DATE,
    raised_by_name   VARCHAR(255)  NOT NULL,
    raised_by_email  VARCHAR(255)  NOT NULL,
    status           VARCHAR(10)   NOT NULL DEFAULT 'OPEN',
    response         TEXT,
    decided_by       UUID,
    decided_by_name  VARCHAR(255),
    decided_at       TIMESTAMPTZ,
    linked_type      VARCHAR(20),                                      -- RFI | CHANGE_ORDER | PROJECT
    linked_id        UUID,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_ppr_kind     CHECK (kind IN ('QUESTION','CHANGE','NEW_PROJECT')),
    CONSTRAINT chk_ppr_status   CHECK (status IN ('OPEN','ACCEPTED','ANSWERED','DECLINED')),
    CONSTRAINT chk_ppr_project  CHECK ((kind = 'NEW_PROJECT') = (project_id IS NULL)),
    CONSTRAINT chk_ppr_decided  CHECK (status = 'OPEN' OR (decided_at IS NOT NULL AND response IS NOT NULL)),
    CONSTRAINT uq_ppr_number    UNIQUE (tenant_id, request_number)
);
CREATE INDEX IF NOT EXISTS idx_ppr_project ON project_portal_requests (tenant_id, project_id, status);
CREATE INDEX IF NOT EXISTS idx_ppr_grant   ON project_portal_requests (grant_id);

-- Files sent by clients. They wait here until staff accept them into the document register or reject them.
CREATE TABLE IF NOT EXISTS project_portal_uploads (
    id                UUID PRIMARY KEY,
    tenant_id         UUID          NOT NULL,
    project_id        UUID          NOT NULL REFERENCES projects(id),
    grant_id          UUID          NOT NULL REFERENCES project_portal_grants(id),
    evidence_id       UUID          NOT NULL,
    file_name         VARCHAR(300)  NOT NULL,
    size_bytes        BIGINT        NOT NULL,
    sha256            VARCHAR(64)   NOT NULL,
    note              VARCHAR(1000),
    uploaded_by_name  VARCHAR(255)  NOT NULL,
    uploaded_by_email VARCHAR(255)  NOT NULL,
    status            VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
    reason            VARCHAR(1000),
    document_id       UUID,                                            -- the register entry once accepted
    decided_by        UUID,
    decided_by_name   VARCHAR(255),
    decided_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_ppu_status   CHECK (status IN ('PENDING','ACCEPTED','REJECTED')),
    CONSTRAINT chk_ppu_accepted CHECK (status <> 'ACCEPTED' OR document_id IS NOT NULL),
    CONSTRAINT chk_ppu_rejected CHECK (status <> 'REJECTED' OR reason IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS idx_ppu_project ON project_portal_uploads (tenant_id, project_id, status);
