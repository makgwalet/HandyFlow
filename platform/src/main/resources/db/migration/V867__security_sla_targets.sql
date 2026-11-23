-- Service-level targets for security incidents: how soon a new incident must be acknowledged and how soon it must be resolved,
-- by severity. A target can be set for one site, or as the company default for every site that has none of its own.
-- Where there is no target, an incident is simply not tracked. Breaches are worked out from the incident times when asked, not stored.
CREATE TABLE IF NOT EXISTS security_sla_targets (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL REFERENCES tenants(id),
    site_id             UUID         REFERENCES security_sites(id),
    severity            VARCHAR(20)  NOT NULL,
    acknowledge_minutes INTEGER      NOT NULL,
    resolve_minutes     INTEGER      NOT NULL,
    updated_by          UUID,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_security_sla_targets PRIMARY KEY (id),
    CONSTRAINT chk_sla_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT chk_sla_acknowledge CHECK (acknowledge_minutes BETWEEN 1 AND 43200),
    CONSTRAINT chk_sla_resolve CHECK (resolve_minutes BETWEEN 1 AND 43200),
    CONSTRAINT chk_sla_order CHECK (resolve_minutes >= acknowledge_minutes)
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_sla_targets_site ON security_sla_targets (tenant_id, site_id, severity) WHERE site_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS ux_sla_targets_company ON security_sla_targets (tenant_id, severity) WHERE site_id IS NULL;

-- Row-level security (V866), if it is installed.
DO $$ BEGIN
    IF to_regproc('hf_rls_apply') IS NOT NULL THEN PERFORM hf_rls_apply(); END IF;
END $$;
