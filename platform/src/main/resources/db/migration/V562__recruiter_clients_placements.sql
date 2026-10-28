-- Recruiter: the companies a recruiter hires for, the people to talk to there, which job is for which client, and what a placement earned.
CREATE TABLE rec_clients (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL,
    name        VARCHAR(150) NOT NULL,
    industry    VARCHAR(80),
    location    VARCHAR(120),
    status      VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    notes       TEXT,
    created_by  UUID,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMP,
    CONSTRAINT pk_rec_clients PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uq_rec_clients_name ON rec_clients (tenant_id, lower(name)) WHERE deleted_at IS NULL;
CREATE INDEX idx_rec_clients_tenant ON rec_clients (tenant_id, status) WHERE deleted_at IS NULL;

CREATE TABLE rec_client_contacts (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL,
    client_id   UUID         NOT NULL REFERENCES rec_clients (id) ON DELETE CASCADE,
    name        VARCHAR(120) NOT NULL,
    email       VARCHAR(200),
    phone       VARCHAR(30),
    role_title  VARCHAR(80),
    is_primary  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_rec_client_contacts PRIMARY KEY (id)
);
CREATE INDEX idx_rec_client_contacts_client ON rec_client_contacts (client_id);

-- A job can be for a client. Taking the client away leaves the job as it was.
ALTER TABLE rec_jobs ADD COLUMN IF NOT EXISTS client_id UUID REFERENCES rec_clients (id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_rec_jobs_client ON rec_jobs (client_id) WHERE client_id IS NOT NULL;

-- What the placement earned, in rand. Only meaningful for a hired candidate on a client's job.
ALTER TABLE rec_applications ADD COLUMN IF NOT EXISTS placement_fee NUMERIC(12, 2) CHECK (placement_fee IS NULL OR placement_fee >= 0);
