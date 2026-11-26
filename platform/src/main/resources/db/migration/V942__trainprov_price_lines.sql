-- Training Provider 0176: programme price books.
-- Fee lines the provider enters for a programme version, optionally for one funder, for a period, each with a note of where the fee comes from.
-- No prices are seeded and no VAT rate is held: the person says whether the amount includes VAT. A line is never edited: a changed fee is a
-- new line, and the old one is withdrawn with a reason. Nothing is deleted. Invoicing only shows a line as a suggestion.

CREATE TABLE trainprov_price_lines (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    programme_version_id UUID NOT NULL REFERENCES trainprov_programme_versions(id),
    funder_id            UUID REFERENCES trainprov_funders(id),
    fee_type             VARCHAR(20) NOT NULL,
    description          VARCHAR(200),
    amount               NUMERIC(14,2) NOT NULL,
    includes_vat         BOOLEAN NOT NULL,
    valid_from           DATE NOT NULL,
    valid_to             DATE,
    source_note          TEXT NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    withdrawn_reason     TEXT,
    withdrawn_by         UUID,
    withdrawn_at         TIMESTAMPTZ,
    created_by           UUID NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    version              BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_trainprov_price_type CHECK (COALESCE(fee_type IN ('TUITION', 'REGISTRATION', 'ASSESSMENT', 'MATERIALS', 'CERTIFICATION', 'OTHER'), FALSE)),
    CONSTRAINT ck_trainprov_price_desc CHECK (fee_type <> 'OTHER' OR COALESCE(char_length(btrim(description)), 0) >= 2),
    CONSTRAINT ck_trainprov_price_amount CHECK (amount > 0 AND amount <= 1000000000),
    CONSTRAINT ck_trainprov_price_dates CHECK (valid_to IS NULL OR valid_to >= valid_from),
    CONSTRAINT ck_trainprov_price_source CHECK (COALESCE(char_length(btrim(source_note)), 0) >= 10),
    CONSTRAINT ck_trainprov_price_status CHECK (COALESCE(status IN ('ACTIVE', 'WITHDRAWN'), FALSE)),
    CONSTRAINT ck_trainprov_price_withdrawn CHECK (
        (status = 'ACTIVE' AND withdrawn_reason IS NULL AND withdrawn_by IS NULL AND withdrawn_at IS NULL)
        OR (status = 'WITHDRAWN' AND COALESCE(char_length(btrim(withdrawn_reason)), 0) >= 10 AND withdrawn_by IS NOT NULL AND withdrawn_at IS NOT NULL))
);
CREATE INDEX idx_trainprov_price_version ON trainprov_price_lines (tenant_id, programme_version_id, status);
