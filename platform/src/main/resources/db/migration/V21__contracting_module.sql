CREATE TABLE contract_templates (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    name            VARCHAR(200) NOT NULL,
    contract_type   VARCHAR(50) NOT NULL
        CHECK (contract_type IN (
            'JOINT_VENTURE','EQUIPMENT_HIRE','EQUIPMENT_LEASE',
            'SERVICE_LEVEL','NDA','SUPPLY','EMPLOYMENT',
            'SUBCONTRACTOR','LOAN','PARTNERSHIP','OTHER'
        )),
    description     TEXT,
    body_template   TEXT NOT NULL,
    variables       JSONB,
    is_system       BOOLEAN NOT NULL DEFAULT false,
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE contracts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    contract_number VARCHAR(30) NOT NULL,
    template_id     UUID REFERENCES contract_templates(id),
    title           VARCHAR(300) NOT NULL,
    contract_type   VARCHAR(50) NOT NULL,
    status          VARCHAR(30) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','UNDER_REVIEW','SENT','SIGNED','EXPIRED','TERMINATED','ARCHIVED')),
    body            TEXT NOT NULL,
    value_amount    NUMERIC(15,2),
    currency        VARCHAR(3) DEFAULT 'ZAR',
    start_date      DATE,
    end_date        DATE,
    auto_renew      BOOLEAN NOT NULL DEFAULT false,
    renewal_notice_days INT DEFAULT 30,
    notes           TEXT,
    sent_at         TIMESTAMP,
    signed_at       TIMESTAMP,
    terminated_at   TIMESTAMP,
    termination_reason TEXT,
    created_by      UUID REFERENCES users(id),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP,
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, contract_number)
);

CREATE TABLE contract_parties (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    contract_id     UUID NOT NULL REFERENCES contracts(id),
    party_type      VARCHAR(20) NOT NULL CHECK (party_type IN ('INTERNAL','EXTERNAL')),
    party_role      VARCHAR(50) NOT NULL,
    full_name       VARCHAR(200) NOT NULL,
    email           VARCHAR(200),
    phone           VARCHAR(30),
    id_number       VARCHAR(30),
    company_name    VARCHAR(200),
    signing_order   INT NOT NULL DEFAULT 1,
    signing_status  VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (signing_status IN ('PENDING','SENT','SIGNED','DECLINED')),
    signed_at       TIMESTAMP,
    sign_ip_address VARCHAR(45),
    sign_user_agent TEXT,
    otp_sent_at     TIMESTAMP,
    otp_verified_at TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE contract_signatures (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    contract_id     UUID NOT NULL REFERENCES contracts(id),
    party_id        UUID NOT NULL REFERENCES contract_parties(id),
    otp_code_hash   VARCHAR(64) NOT NULL,
    phone_last4     VARCHAR(4),
    ip_address      VARCHAR(45),
    user_agent      TEXT,
    signed_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    signature_data  TEXT,
    CONSTRAINT no_duplicate_signature UNIQUE (contract_id, party_id)
);

CREATE TABLE contract_comments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    contract_id     UUID NOT NULL REFERENCES contracts(id),
    user_id         UUID REFERENCES users(id),
    comment         TEXT NOT NULL,
    clause_ref      VARCHAR(100),
    resolved        BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE contract_attachments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    contract_id     UUID NOT NULL REFERENCES contracts(id),
    file_name       VARCHAR(300) NOT NULL,
    file_size_bytes BIGINT,
    mime_type       VARCHAR(100),
    storage_key     VARCHAR(500),
    uploaded_by     UUID REFERENCES users(id),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_contracts_tenant   ON contracts(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_contracts_status   ON contracts(tenant_id, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_contracts_end_date ON contracts(tenant_id, end_date) WHERE status = 'SIGNED';
CREATE INDEX idx_contract_parties_contract    ON contract_parties(contract_id);
CREATE INDEX idx_contract_signatures_contract ON contract_signatures(contract_id);
CREATE INDEX idx_contract_templates_tenant    ON contract_templates(tenant_id) WHERE deleted_at IS NULL;