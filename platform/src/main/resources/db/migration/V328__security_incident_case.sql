-- Incident case management: an assignee and an append-only timeline. Evidence files go through the shared
-- evidence module (entity type Incident). Existing incidents get a timeline built from their own timestamps.

ALTER TABLE security_incidents
    ADD COLUMN IF NOT EXISTS assignee_name VARCHAR(200),
    ADD COLUMN IF NOT EXISTS assigned_at   TIMESTAMPTZ;

CREATE TABLE security_incident_events (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    incident_id UUID NOT NULL REFERENCES security_incidents(id),
    event_type  VARCHAR(40) NOT NULL,
    to_status   VARCHAR(30),
    note        TEXT,
    by_user     UUID,
    by_name     VARCHAR(200),
    at          TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_security_incident_events ON security_incident_events (tenant_id, incident_id, at);

INSERT INTO security_incident_events (id, tenant_id, incident_id, event_type, to_status, at)
SELECT gen_random_uuid(), tenant_id, id, 'REPORTED', 'OPEN', created_at FROM security_incidents;

INSERT INTO security_incident_events (id, tenant_id, incident_id, event_type, to_status, by_user, at)
SELECT gen_random_uuid(), tenant_id, id, 'ACKNOWLEDGED', 'ACKNOWLEDGED', acknowledged_by, acknowledged_at
FROM security_incidents WHERE acknowledged_at IS NOT NULL;

INSERT INTO security_incident_events (id, tenant_id, incident_id, event_type, to_status, by_user, at)
SELECT gen_random_uuid(), tenant_id, id, 'RESOLVED', 'RESOLVED', resolved_by, resolved_at
FROM security_incidents WHERE resolved_at IS NOT NULL;
