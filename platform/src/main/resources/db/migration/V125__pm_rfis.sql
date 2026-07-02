-- V125__pm_rfis.sql
-- RFI (Request for Information) workflow table.
-- Status machine: DRAFT → SUBMITTED → RESPONDED → CLOSED | CANCELLED

CREATE TABLE public.project_rfis (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID            NOT NULL,
    project_id          UUID            NOT NULL REFERENCES public.projects(id),

    -- Identifier
    rfi_number          VARCHAR(20)     NOT NULL,       -- RFI-001, RFI-002 ...

    -- Content
    title               VARCHAR(500)    NOT NULL,
    description         TEXT,
    category            VARCHAR(50),                    -- DESIGN|SITE|MATERIALS|SAFETY|SPECIFICATION|OTHER

    -- Request side
    requested_by        VARCHAR(255),
    requested_by_id     UUID,
    requested_date      DATE            NOT NULL DEFAULT CURRENT_DATE,
    due_date            DATE,

    -- Response side
    responded_by        VARCHAR(255),
    responded_by_id     UUID,
    responded_date      DATE,
    response            TEXT,

    -- Lifecycle
    status              VARCHAR(30)     NOT NULL DEFAULT 'DRAFT',   -- DRAFT|SUBMITTED|RESPONDED|CLOSED|CANCELLED
    closed_at           TIMESTAMPTZ,
    cancelled_at        TIMESTAMPTZ,
    cancellation_reason TEXT,

    -- Timestamps
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- Tenant isolation
CREATE INDEX idx_project_rfis_tenant    ON public.project_rfis (tenant_id);
CREATE INDEX idx_project_rfis_project   ON public.project_rfis (project_id);
CREATE INDEX idx_project_rfis_status    ON public.project_rfis (project_id, status);

-- Each project's RFI numbers are unique
CREATE UNIQUE INDEX idx_project_rfis_number ON public.project_rfis (project_id, rfi_number);

COMMENT ON TABLE  public.project_rfis IS 'Tracked RFI (Request for Information) workflow per project';
COMMENT ON COLUMN public.project_rfis.status IS 'DRAFT → SUBMITTED → RESPONDED → CLOSED | CANCELLED';
