-- Quotes the FM company sends to a client, which the client accepts or declines in the portal.
CREATE TABLE IF NOT EXISTS fm_quotes (
    id                        UUID PRIMARY KEY,
    tenant_id                 UUID          NOT NULL,
    quote_number              VARCHAR(30)   NOT NULL,
    client_id                 UUID          NOT NULL,
    site_id                   UUID          NOT NULL,
    asset_id                  UUID,
    work_order_id             UUID,
    title                     VARCHAR(200)  NOT NULL,
    description               VARCHAR(2000),
    status                    VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    valid_until               DATE          NOT NULL,
    subtotal                  NUMERIC(15,2) NOT NULL DEFAULT 0,
    vat_rate                  NUMERIC(5,2)  NOT NULL DEFAULT 0,
    vat_amount                NUMERIC(15,2) NOT NULL DEFAULT 0,
    total                     NUMERIC(15,2) NOT NULL DEFAULT 0,
    internal_note             VARCHAR(1000),
    sent_at                   TIMESTAMPTZ,
    sent_by_user_id           UUID,
    decided_at                TIMESTAMPTZ,
    decided_by_portal_user_id UUID,
    decision_note             VARCHAR(1000),
    created_by_user_id        UUID,
    created_at                TIMESTAMPTZ   NOT NULL,
    updated_at                TIMESTAMPTZ   NOT NULL,
    version                   BIGINT,
    CONSTRAINT uq_fm_quote_number UNIQUE (tenant_id, quote_number),
    CONSTRAINT chk_fm_quote_status CHECK (status IN ('DRAFT','SENT','ACCEPTED','DECLINED','EXPIRED','WITHDRAWN'))
);
CREATE INDEX IF NOT EXISTS idx_fm_quotes_client ON fm_quotes (tenant_id, client_id, status);
CREATE INDEX IF NOT EXISTS idx_fm_quotes_expiry ON fm_quotes (valid_until) WHERE status = 'SENT';

CREATE TABLE IF NOT EXISTS fm_quote_lines (
    id          UUID PRIMARY KEY,
    tenant_id   UUID          NOT NULL,
    quote_id    UUID          NOT NULL,
    line_no     INT           NOT NULL,
    description VARCHAR(500)  NOT NULL,
    quantity    NUMERIC(12,3) NOT NULL,
    unit_price  NUMERIC(15,2) NOT NULL,
    line_total  NUMERIC(15,2) NOT NULL,
    CONSTRAINT chk_fm_quote_line_qty CHECK (quantity > 0),
    CONSTRAINT chk_fm_quote_line_price CHECK (unit_price >= 0)
);
CREATE INDEX IF NOT EXISTS idx_fm_quote_lines_quote ON fm_quote_lines (tenant_id, quote_id);
