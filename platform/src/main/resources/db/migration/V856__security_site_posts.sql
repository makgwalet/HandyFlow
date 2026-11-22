-- What a site needs: a post is a named demand ("Main gate, nights, 2 guards") on given weekdays and times.
-- Coverage is measured against these. No post means no demand, so nothing is reported short.
CREATE TABLE security_site_posts (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL,
    site_id     UUID NOT NULL,
    name        VARCHAR(80) NOT NULL,
    weekdays    SMALLINT[] NOT NULL CHECK (cardinality(weekdays) BETWEEN 1 AND 7 AND weekdays <@ ARRAY[1,2,3,4,5,6,7]::smallint[]),
    start_time  TIME NOT NULL,
    end_time    TIME NOT NULL,          -- earlier than or equal to start_time means it runs past midnight (equal: 24 hours)
    min_guards  INTEGER NOT NULL CHECK (min_guards BETWEEN 1 AND 50),
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_by  UUID,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deactivated_at TIMESTAMPTZ,
    deactivated_by UUID
);
CREATE INDEX idx_site_posts_tenant_site ON security_site_posts (tenant_id, site_id) WHERE active;
