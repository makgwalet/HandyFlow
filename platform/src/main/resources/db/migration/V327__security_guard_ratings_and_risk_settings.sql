-- Guard ratings (client or supervisor, 1 to 5 across six dimensions) and per-tenant risk thresholds.
-- The operational score and risk recommendations are computed on request from existing records; only the inputs are stored here.

CREATE TABLE security_guard_ratings (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    guard_id          UUID NOT NULL REFERENCES security_guards(id),
    site_id           UUID,
    source            VARCHAR(20) NOT NULL CHECK (source IN ('CLIENT', 'SUPERVISOR')),
    rater_name        VARCHAR(200),
    rated_on          DATE NOT NULL,
    punctuality       SMALLINT NOT NULL CHECK (punctuality BETWEEN 1 AND 5),
    professionalism   SMALLINT NOT NULL CHECK (professionalism BETWEEN 1 AND 5),
    appearance        SMALLINT NOT NULL CHECK (appearance BETWEEN 1 AND 5),
    communication     SMALLINT NOT NULL CHECK (communication BETWEEN 1 AND 5),
    alertness         SMALLINT NOT NULL CHECK (alertness BETWEEN 1 AND 5),
    incident_handling SMALLINT NOT NULL CHECK (incident_handling BETWEEN 1 AND 5),
    comment           TEXT,
    created_by        UUID,
    created_by_name   VARCHAR(200),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_security_guard_ratings_guard ON security_guard_ratings (tenant_id, guard_id, rated_on DESC);

CREATE TABLE security_risk_settings (
    id                           UUID PRIMARY KEY,
    tenant_id                    UUID NOT NULL UNIQUE,
    review_at                    INT NOT NULL DEFAULT 1 CHECK (review_at >= 1),
    warning_at                   INT NOT NULL DEFAULT 3 CHECK (warning_at >= 1),
    investigation_at             INT NOT NULL DEFAULT 5 CHECK (investigation_at >= 1),
    window_days                  INT NOT NULL DEFAULT 90 CHECK (window_days BETWEEN 7 AND 365),
    misconduct_at                INT NOT NULL DEFAULT 2 CHECK (misconduct_at >= 1),
    misconduct_window_days       INT NOT NULL DEFAULT 365 CHECK (misconduct_window_days BETWEEN 30 AND 730),
    suspension_review_on_critical BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by_name              VARCHAR(200)
);
