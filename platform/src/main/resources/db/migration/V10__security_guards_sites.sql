-- Guards: PSiRA-registered security officers per tenant
CREATE TABLE security_guards (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    psira_number    VARCHAR(20),
    id_number       VARCHAR(13),
    phone           VARCHAR(20),
    photo_url       VARCHAR(500),
    grade           VARCHAR(5),   -- PSiRA grade: A, B, C, D, E
    active          BOOLEAN     NOT NULL DEFAULT true,
    notes           TEXT,
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMP,
    deleted_by      UUID,
    version         BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_security_guards PRIMARY KEY (id),
    CONSTRAINT uq_guard_psira_per_tenant
        UNIQUE (tenant_id, psira_number)
);

-- Sites: client locations that guards are deployed to
CREATE TABLE security_sites (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL,
    customer_id     UUID,           -- FK to CRM customers
    name            VARCHAR(255) NOT NULL,
    address         JSONB,
    latitude        DECIMAL(10,7),
    longitude       DECIMAL(10,7),
    contact_name    VARCHAR(100),
    contact_phone   VARCHAR(20),
    instructions    TEXT,
    -- WHY qr_secret? Each site has a secret used to sign checkpoint QR codes.
    -- Guards scan the QR → server validates the HMAC signature → logs the scan.
    -- Rotating the secret invalidates all existing QR codes for that site.
    qr_secret VARCHAR(64) NOT NULL DEFAULT replace(gen_random_uuid()::text, '-', ''),
    active          BOOLEAN     NOT NULL DEFAULT true,
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMP,
    deleted_by      UUID,
    version         BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_security_sites PRIMARY KEY (id)
);

-- Checkpoints: specific scan points within a site (gates, rooms, etc.)
CREATE TABLE security_checkpoints (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL,
    site_id         UUID        NOT NULL,
    name            VARCHAR(100) NOT NULL,   -- "North Gate", "Server Room"
    description     TEXT,
    qr_code         VARCHAR(255) NOT NULL,   -- unique QR payload
    sort_order      INTEGER     NOT NULL DEFAULT 0,
    active          BOOLEAN     NOT NULL DEFAULT true,
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT now(),

    CONSTRAINT pk_security_checkpoints PRIMARY KEY (id),
    CONSTRAINT fk_checkpoint_site
        FOREIGN KEY (site_id) REFERENCES security_sites(id) ON DELETE CASCADE,
    CONSTRAINT uq_checkpoint_qr UNIQUE (qr_code)
);

CREATE INDEX idx_guards_tenant   ON security_guards(tenant_id)    WHERE deleted_at IS NULL;
CREATE INDEX idx_sites_tenant    ON security_sites(tenant_id)     WHERE deleted_at IS NULL;
CREATE INDEX idx_checkpoints_site ON security_checkpoints(site_id) WHERE active = true;