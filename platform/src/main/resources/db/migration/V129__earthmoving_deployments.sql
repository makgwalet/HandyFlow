-- V___earthmoving_deployments.sql
-- (rename to the next available Flyway version number in your sequence)
--
-- Deployment history — previously EarthAsset.current_site/current_client
-- only ever held the LATEST value, silently overwritten on every
-- redeployment, with no record of when a deployment started, who the site
-- contact was, or why it ended. This table is the append-only history;
-- current_site/current_client stay as-is for cheap "where is it right now"
-- reads in list views.
CREATE TABLE earthmoving_deployments (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID        NOT NULL,
    asset_id            UUID        NOT NULL,
    site_name           VARCHAR(255) NOT NULL,
    client_name         VARCHAR(255),
    contact_name        VARCHAR(200),
    contact_phone       VARCHAR(50),
    planned_start_date  DATE,
    planned_end_date    DATE,
    deployed_at         TIMESTAMP   NOT NULL DEFAULT now(),
    returned_at         TIMESTAMP,
    end_reason          VARCHAR(20),   -- AVAILABLE | BREAKDOWN | MAINTENANCE — see EarthAssetService; null while open
    notes               TEXT,
    created_at          TIMESTAMP   NOT NULL DEFAULT now(),

    CONSTRAINT pk_earthmoving_deployments PRIMARY KEY (id),
    CONSTRAINT fk_deployment_asset FOREIGN KEY (asset_id) REFERENCES earthmoving_assets(id),
    CONSTRAINT chk_deployment_end_reason CHECK (end_reason IN ('AVAILABLE', 'BREAKDOWN', 'MAINTENANCE'))
);

-- Deployment history for a specific asset, newest first — the main query
-- for the "deployment history" tab on an asset's profile.
CREATE INDEX idx_em_deployments_asset ON earthmoving_deployments (asset_id, deployed_at DESC);

-- Enforces at most one OPEN deployment per asset at the database level —
-- application code already guarantees this (deploy() is only legal from
-- AVAILABLE), but a partial unique index makes it impossible even via a
-- direct SQL edit or a future bug, not just "unlikely".
CREATE UNIQUE INDEX uq_em_deployments_one_open_per_asset
    ON earthmoving_deployments (asset_id)
    WHERE returned_at IS NULL;
