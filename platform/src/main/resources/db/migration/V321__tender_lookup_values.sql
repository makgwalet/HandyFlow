-- Values a company has added to the pick-lists on the compliance and tender screens (document types, authorities, roles...).
-- The built-in lists live in the app; these are additions for one tenant only. A value is unique per list ignoring case.
CREATE TABLE tender_lookup_values (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    list_key    VARCHAR(40) NOT NULL,
    value       VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by  UUID
);
CREATE UNIQUE INDEX uq_tender_lookup_values ON tender_lookup_values(tenant_id, list_key, LOWER(value));
