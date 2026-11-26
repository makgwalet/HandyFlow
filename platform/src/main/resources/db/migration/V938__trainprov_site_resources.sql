-- Training Provider 0172: site resource register.
-- Equipment, vehicles, practical kits and similar resources that belong to a delivery site (and optionally one venue there). This is a
-- register only: it says what exists, where, and in what state. It does not book resources and says nothing about whether a resource
-- satisfies any standard. Every change of state is written to an event row with a reason. Nothing is deleted: a resource is retired with a reason.

CREATE TABLE trainprov_site_resources (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    resource_no     VARCHAR(30) NOT NULL,
    site_id         UUID NOT NULL REFERENCES trainprov_delivery_sites(id),
    venue_id        UUID REFERENCES trainprov_venues(id),
    name            VARCHAR(200) NOT NULL,
    resource_type   VARCHAR(30) NOT NULL,
    identifier      VARCHAR(100),
    quantity        INTEGER NOT NULL DEFAULT 1,
    shareable       BOOLEAN NOT NULL DEFAULT FALSE,
    notes           TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'IN_SERVICE',
    status_reason   TEXT,
    status_changed_on DATE,
    created_by      UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_trainprov_resource_no UNIQUE (tenant_id, resource_no),
    CONSTRAINT ck_trainprov_resource_name CHECK (COALESCE(char_length(btrim(name)), 0) >= 2),
    CONSTRAINT ck_trainprov_resource_type CHECK (COALESCE(resource_type IN ('EQUIPMENT', 'VEHICLE', 'PRACTICAL_KIT', 'SIMULATOR', 'IT_EQUIPMENT', 'CONSUMABLES', 'OTHER'), FALSE)),
    CONSTRAINT ck_trainprov_resource_qty CHECK (quantity >= 1 AND quantity <= 100000),
    CONSTRAINT ck_trainprov_resource_status CHECK (COALESCE(status IN ('IN_SERVICE', 'UNDER_REPAIR', 'OUT_OF_SERVICE', 'RETIRED'), FALSE)),
    CONSTRAINT ck_trainprov_resource_reason CHECK (
        (status = 'IN_SERVICE' AND status_reason IS NULL)
        OR (status <> 'IN_SERVICE' AND COALESCE(char_length(btrim(status_reason)), 0) >= 10 AND status_changed_on IS NOT NULL))
);
-- A tagged item (serial number, asset tag, registration) is unique within the tenant while it is not retired.
CREATE UNIQUE INDEX uq_trainprov_resource_identifier ON trainprov_site_resources (tenant_id, lower(btrim(identifier)))
    WHERE identifier IS NOT NULL AND btrim(identifier) <> '' AND status <> 'RETIRED';
CREATE INDEX idx_trainprov_resource_site ON trainprov_site_resources (tenant_id, site_id, status);

CREATE TABLE trainprov_site_resource_events (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    resource_id   UUID NOT NULL REFERENCES trainprov_site_resources(id),
    event_type    VARCHAR(20) NOT NULL CHECK (event_type IN ('ADDED', 'CHANGED', 'MOVED', 'STATUS')),
    from_status   VARCHAR(20),
    to_status     VARCHAR(20),
    from_site_id  UUID,
    to_site_id    UUID,
    note          TEXT,
    occurred_on   DATE NOT NULL,
    created_by    UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_trainprov_resource_event_note CHECK (event_type IN ('ADDED', 'CHANGED') OR COALESCE(char_length(btrim(note)), 0) >= 10)
);
CREATE INDEX idx_trainprov_resource_event ON trainprov_site_resource_events (tenant_id, resource_id, created_at);
