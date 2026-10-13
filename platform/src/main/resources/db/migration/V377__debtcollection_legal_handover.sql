-- RENAME to the next free Flyway version AFTER the Chunk 4 communications migration.
-- Same base-column ASSUMPTION as earlier chunks (id, tenant_id, created_at, updated_at, version) — align with AggregateRoot.

CREATE TABLE debtcollection_legal_handovers (
    id                        UUID PRIMARY KEY,
    tenant_id                 UUID         NOT NULL,
    created_at                TIMESTAMPTZ  NOT NULL,
    updated_at                TIMESTAMPTZ,
    version                   BIGINT       NOT NULL DEFAULT 0,
    case_id                   UUID         NOT NULL REFERENCES debtcollection_cases (id),
    status                    VARCHAR(15)  NOT NULL,
    agreement_type            VARCHAR(25)  NOT NULL,
    creditor_authority_note   TEXT,
    prescription_reviewed     BOOLEAN      NOT NULL DEFAULT FALSE,
    prescription_review_note  TEXT,
    prescription_reviewed_by  UUID,
    prescription_reviewed_at  TIMESTAMPTZ,
    notes                     TEXT,
    package_version           INT          NOT NULL DEFAULT 0,
    package_json              TEXT,
    package_hash              VARCHAR(64),
    package_built_at          TIMESTAMPTZ,
    prepared_by               UUID         NOT NULL,
    prepared_by_name          VARCHAR(255),
    submitted_at              TIMESTAMPTZ,
    approved_by               UUID,
    approved_by_name          VARCHAR(255),
    approved_at               TIMESTAMPTZ,
    last_return_reason        VARCHAR(500),
    legal_matter_id           UUID,
    legal_matter_number       VARCHAR(60),
    transferred_at            TIMESTAMPTZ,
    transferred_by            UUID,
    legal_status              VARCHAR(100),
    legal_status_updated_at   TIMESTAMPTZ,
    cancel_reason             VARCHAR(500)
);
CREATE INDEX ix_dc_handovers_case ON debtcollection_legal_handovers (tenant_id, case_id);
CREATE INDEX ix_dc_handovers_matter ON debtcollection_legal_handovers (tenant_id, legal_matter_id) WHERE legal_matter_id IS NOT NULL;
-- Hard guarantee behind the "one open handover per case" check (closes the race between two preparers).
CREATE UNIQUE INDEX uq_dc_handovers_one_open ON debtcollection_legal_handovers (case_id)
    WHERE status IN ('DRAFT','IN_REVIEW','APPROVED');

CREATE TABLE debtcollection_legal_handover_evidence (
    handover_id  UUID NOT NULL REFERENCES debtcollection_legal_handovers (id),
    evidence_id  UUID NOT NULL,
    PRIMARY KEY (handover_id, evidence_id)
);

CREATE TABLE debtcollection_legal_notices (
    id                           UUID PRIMARY KEY,
    tenant_id                    UUID         NOT NULL,
    created_at                   TIMESTAMPTZ  NOT NULL,
    updated_at                   TIMESTAMPTZ,
    version                      BIGINT       NOT NULL DEFAULT 0,
    case_id                      UUID         NOT NULL REFERENCES debtcollection_cases (id),
    notice_type                  VARCHAR(20)  NOT NULL,
    notice_version               VARCHAR(60)  NOT NULL,
    address_used                 VARCHAR(500) NOT NULL,
    address_source               VARCHAR(30),
    dispatch_method              VARCHAR(20),
    dispatched_date              DATE,
    tracking_reference           VARCHAR(100),
    post_office_branch           VARCHAR(150),
    collection_notice_reference  VARCHAR(100),
    delivery_status              VARCHAR(25)  NOT NULL,
    delivered_date               DATE,
    delivery_detail              VARCHAR(500),
    default_date                 DATE,
    communication_id             UUID,
    evidence_id                  UUID,
    review_status                VARCHAR(15)  NOT NULL,
    reviewed_by                  UUID,
    reviewed_by_name             VARCHAR(255),
    reviewed_at                  TIMESTAMPTZ,
    review_note                  TEXT,
    notes                        TEXT,
    recorded_by                  UUID         NOT NULL,
    recorded_by_name             VARCHAR(255)
);
CREATE INDEX ix_dc_notices_case ON debtcollection_legal_notices (tenant_id, case_id);

CREATE TABLE debtcollection_legal_events (
    id               UUID PRIMARY KEY,
    tenant_id        UUID         NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ,
    version          BIGINT       NOT NULL DEFAULT 0,
    subject_type     VARCHAR(15)  NOT NULL,
    subject_id       UUID         NOT NULL,
    case_id          UUID         NOT NULL,
    event_type       VARCHAR(40)  NOT NULL,
    detail           TEXT,
    actor_user_id    UUID,
    actor_user_name  VARCHAR(255)
);
CREATE INDEX ix_dc_legal_events_subject ON debtcollection_legal_events (subject_id, created_at);
