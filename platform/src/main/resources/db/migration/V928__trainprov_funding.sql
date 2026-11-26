-- Training provider: who pays. A funder register (the learner, an employer, a SETA, a grant, government or another payer) and links from enrolments to
-- the funders paying for them. A link records who is paying, from when, and the agreement or the funder's own reference. No amounts, rates, percentages
-- or eligibility rules are held here, and a link does not mean any funding was approved or paid. Nothing is deleted: funders are retired and links ended, with a reason.

CREATE TABLE trainprov_funders (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    funder_no        VARCHAR(30) NOT NULL,
    name             VARCHAR(200) NOT NULL,
    funder_type      VARCHAR(20) NOT NULL CHECK (funder_type IN ('SELF', 'EMPLOYER', 'SETA', 'GRANT', 'GOVERNMENT', 'OTHER')),
    client_id        UUID REFERENCES trainprov_clients(id),
    contact_name     VARCHAR(200),
    contact_email    VARCHAR(200),
    contact_phone    VARCHAR(50),
    note             TEXT,
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'RETIRED')),
    retire_reason    TEXT,
    retired_by       UUID,
    created_by       UUID NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    version          BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_trainprov_funder_no UNIQUE (tenant_id, funder_no),
    CONSTRAINT ck_trainprov_funder_name CHECK (COALESCE(char_length(btrim(name)), 0) >= 2),
    CONSTRAINT ck_trainprov_funder_retire CHECK (
        (status = 'ACTIVE' AND retire_reason IS NULL AND retired_by IS NULL)
        OR (status = 'RETIRED' AND COALESCE(char_length(btrim(retire_reason)), 0) >= 10 AND retired_by IS NOT NULL))
);
CREATE UNIQUE INDEX uq_trainprov_funder_live_name ON trainprov_funders (tenant_id, lower(name)) WHERE status = 'ACTIVE';

CREATE TABLE trainprov_enrolment_funding (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    enrolment_id      UUID NOT NULL REFERENCES trainprov_learner_enrolments(id),
    funder_id         UUID NOT NULL REFERENCES trainprov_funders(id),
    funded_from       DATE NOT NULL,
    agreement_ref     VARCHAR(200),
    funder_reference  VARCHAR(200),
    note              TEXT,
    status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ENDED')),
    ended_on          DATE,
    end_reason        TEXT,
    ended_by          UUID,
    created_by        UUID NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_trainprov_funding_end CHECK (
        (status = 'ACTIVE' AND ended_on IS NULL AND end_reason IS NULL AND ended_by IS NULL)
        OR (status = 'ENDED' AND ended_on IS NOT NULL AND ended_on >= funded_from AND COALESCE(char_length(btrim(end_reason)), 0) >= 10 AND ended_by IS NOT NULL))
);
-- One live link per enrolment and funder.
CREATE UNIQUE INDEX uq_trainprov_funding_live ON trainprov_enrolment_funding (tenant_id, enrolment_id, funder_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_trainprov_funding_enrolment ON trainprov_enrolment_funding (tenant_id, enrolment_id);
CREATE INDEX idx_trainprov_funding_funder ON trainprov_enrolment_funding (tenant_id, funder_id);
