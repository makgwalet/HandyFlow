-- Earthmoving assets (heavy machines) per tenant
CREATE TABLE earthmoving_assets (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID        NOT NULL,
    -- WHY customer_id? Asset may belong to/be hired to a CRM customer
    customer_id         UUID,
    name                VARCHAR(255) NOT NULL,
    make                VARCHAR(100),           -- Caterpillar, Komatsu, Volvo
    model               VARCHAR(100),           -- D9, PC200, EC480
    year                INTEGER,
    serial_number       VARCHAR(100),
    registration        VARCHAR(20),
    asset_type          VARCHAR(50)  NOT NULL,  -- DOZER, EXCAVATOR, GRADER, LOADER, DUMPER, CRANE, OTHER
    status              VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE',
    -- WHY store hourly/daily rate? Used in job costing quotes
    hourly_rate         NUMERIC(15,2),
    daily_rate          NUMERIC(15,2),
    purchase_date       DATE,
    purchase_price      NUMERIC(15,2),
    -- Service tracking
    last_service_hours  NUMERIC(10,1) DEFAULT 0,
    current_hours       NUMERIC(10,1) DEFAULT 0,
    service_interval_hours NUMERIC(10,1) DEFAULT 250,
    notes               TEXT,
    photo_url           VARCHAR(500),
    created_at          TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP   NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMP,
    deleted_by          UUID,
    version             BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_earthmoving_assets PRIMARY KEY (id),
    CONSTRAINT chk_asset_status CHECK (
        status IN ('AVAILABLE','DEPLOYED','MAINTENANCE','BREAKDOWN','RETIRED')
    ),
    CONSTRAINT chk_asset_type CHECK (
        asset_type IN ('DOZER','EXCAVATOR','GRADER','LOADER','DUMPER','CRANE','ROLLER','SCRAPER','OTHER')
    )
);

-- Maintenance records for each asset
CREATE TABLE earthmoving_maintenance (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL,
    asset_id        UUID        NOT NULL,
    type            VARCHAR(50) NOT NULL,  -- SERVICE, REPAIR, INSPECTION, TYRE, OTHER
    description     TEXT        NOT NULL,
    performed_at    TIMESTAMP   NOT NULL,
    hours_at_service NUMERIC(10,1),
    next_service_hours NUMERIC(10,1),
    cost            NUMERIC(15,2),
    supplier        VARCHAR(255),
    invoice_ref     VARCHAR(100),
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMP,
    version         BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_earthmoving_maintenance PRIMARY KEY (id),
    CONSTRAINT fk_maintenance_asset
        FOREIGN KEY (asset_id) REFERENCES earthmoving_assets(id),
    CONSTRAINT chk_maintenance_type CHECK (
        type IN ('SERVICE','REPAIR','INSPECTION','TYRE','BATTERY','OTHER')
    )
);

-- Operator logs: who operated which machine, when, for how long
CREATE TABLE earthmoving_operator_logs (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL,
    asset_id        UUID        NOT NULL,
    -- WHY guard_id? Reuse security module guards as operators (same people)
    -- For companies without security module, operator_name is used instead
    guard_id        UUID,
    operator_name   VARCHAR(200),
    site_name       VARCHAR(255),
    started_at      TIMESTAMP   NOT NULL,
    ended_at        TIMESTAMP,
    hours_logged    NUMERIC(10,2),
    fuel_used_litres NUMERIC(10,2),
    start_hours     NUMERIC(10,1),
    end_hours       NUMERIC(10,1),
    notes           TEXT,
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),

    CONSTRAINT pk_operator_logs PRIMARY KEY (id),
    CONSTRAINT fk_oplog_asset
        FOREIGN KEY (asset_id) REFERENCES earthmoving_assets(id)
);

CREATE INDEX idx_em_assets_tenant    ON earthmoving_assets(tenant_id)       WHERE deleted_at IS NULL;
CREATE INDEX idx_em_assets_status    ON earthmoving_assets(tenant_id, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_em_maintenance_asset ON earthmoving_maintenance(asset_id)   WHERE deleted_at IS NULL;
CREATE INDEX idx_em_oplogs_asset     ON earthmoving_operator_logs(asset_id, started_at);