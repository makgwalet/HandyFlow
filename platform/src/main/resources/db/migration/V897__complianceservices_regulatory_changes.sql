-- Compliance Services: regulatory change monitoring. A change is recorded against a source, linked to the catalogue obligations
-- it touches, and confirmed or dismissed by a reviewer (a second person, or the same person with a recorded reason). Confirming
-- raises one impact per client the change touches. Nothing here edits the obligation catalogue or any past assessment.

CREATE TABLE IF NOT EXISTS compliance_reg_sources (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    name        VARCHAR(200) NOT NULL,
    authority   VARCHAR(200),
    url         VARCHAR(500),
    kind        VARCHAR(12) NOT NULL DEFAULT 'OTHER',
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_by  UUID,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_compliance_reg_source_kind CHECK (kind IN ('LEGISLATION', 'REGULATION', 'NOTICE', 'GUIDANCE', 'OTHER'))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_compliance_reg_source_name ON compliance_reg_sources(tenant_id, lower(name));

CREATE TABLE IF NOT EXISTS compliance_reg_changes (
    id                     UUID PRIMARY KEY,
    tenant_id              UUID NOT NULL,
    source_id              UUID NOT NULL REFERENCES compliance_reg_sources(id),
    title                  VARCHAR(250) NOT NULL,
    summary                TEXT,
    change_type            VARCHAR(10) NOT NULL,
    publication_date       DATE,
    effective_date         DATE,
    reference_url          VARCHAR(500),
    origin                 VARCHAR(10) NOT NULL DEFAULT 'MANUAL',
    status                 VARCHAR(10) NOT NULL DEFAULT 'DETECTED',
    raised_by              UUID,
    raised_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    review_started_by      UUID,
    review_started_at      TIMESTAMPTZ,
    reviewed_by            UUID,
    reviewed_at            TIMESTAMPTZ,
    review_note            TEXT,
    review_exception_reason TEXT,
    impacts_raised_at      TIMESTAMPTZ,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version                BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_compliance_reg_change_type CHECK (change_type IN ('NEW', 'AMENDED', 'REPEALED', 'PROPOSED', 'GUIDANCE')),
    CONSTRAINT ck_compliance_reg_change_origin CHECK (origin IN ('MANUAL', 'AUTOMATED')),
    CONSTRAINT ck_compliance_reg_change_status CHECK (status IN ('DETECTED', 'IN_REVIEW', 'CONFIRMED', 'DISMISSED')),
    CONSTRAINT ck_compliance_reg_change_reviewed CHECK (status NOT IN ('CONFIRMED', 'DISMISSED')
        OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL AND review_note IS NOT NULL)),
    CONSTRAINT ck_compliance_reg_change_effective CHECK (status <> 'CONFIRMED' OR effective_date IS NOT NULL OR change_type IN ('PROPOSED', 'GUIDANCE'))
);
CREATE INDEX IF NOT EXISTS idx_compliance_reg_change_tenant ON compliance_reg_changes(tenant_id, status);

CREATE TABLE IF NOT EXISTS compliance_reg_change_obligations (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    change_id     UUID NOT NULL REFERENCES compliance_reg_changes(id) ON DELETE CASCADE,
    obligation_id UUID NOT NULL REFERENCES compliance_obligations(id),
    CONSTRAINT uq_compliance_reg_change_obligation UNIQUE (change_id, obligation_id)
);

CREATE TABLE IF NOT EXISTS compliance_reg_impacts (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    change_id        UUID NOT NULL REFERENCES compliance_reg_changes(id),
    client_id        UUID NOT NULL REFERENCES compliance_service_clients(id),
    obligation_id    UUID NOT NULL REFERENCES compliance_obligations(id),
    status           VARCHAR(12) NOT NULL DEFAULT 'OPEN',
    due_date         DATE,
    note             TEXT,
    handled_by       UUID,
    handled_at       TIMESTAMPTZ,
    notice_given_at  TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version          BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_compliance_reg_impact UNIQUE (change_id, client_id, obligation_id),
    CONSTRAINT ck_compliance_reg_impact_status CHECK (status IN ('OPEN', 'NOTICE_GIVEN', 'NO_ACTION', 'DONE')),
    CONSTRAINT ck_compliance_reg_impact_handled CHECK (status = 'OPEN' OR (handled_by IS NOT NULL AND handled_at IS NOT NULL AND note IS NOT NULL)),
    CONSTRAINT ck_compliance_reg_impact_notice CHECK (status <> 'NOTICE_GIVEN' OR notice_given_at IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS idx_compliance_reg_impact_client ON compliance_reg_impacts(tenant_id, client_id);
CREATE INDEX IF NOT EXISTS idx_compliance_reg_impact_open ON compliance_reg_impacts(tenant_id) WHERE status IN ('OPEN', 'NOTICE_GIVEN');
