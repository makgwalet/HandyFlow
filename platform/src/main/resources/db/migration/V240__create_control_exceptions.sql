-- Placeholder version — confirm real next version before applying.
--
-- Stage 1 of the Financial Control & Assurance plan, Option C: the
-- shared "needs attention" board. Existing checks (SCM's three-way
-- match) stay completely untouched — this table only ever receives an
-- ADDITIONAL copy of "here's a problem" at the same moment the existing
-- check already flags something, via ScmService's existing DISPUTE
-- notification block, not by touching performThreeWayMatch() itself.
CREATE TABLE control_exceptions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,

    source_module       VARCHAR(50)  NOT NULL,  -- e.g. 'supplychain'
    control_type        VARCHAR(100) NOT NULL,  -- e.g. 'THREE_WAY_MATCH_DISPUTE'
    related_entity_type VARCHAR(100) NOT NULL,  -- e.g. 'ScSupplierInvoice'
    related_entity_id   UUID NOT NULL,

    severity            VARCHAR(20)  NOT NULL DEFAULT 'WARNING',
    description         TEXT NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'OPEN',

    detected_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at         TIMESTAMPTZ,
    resolved_by         UUID,
    resolved_by_name    VARCHAR(255),
    resolution_notes    TEXT,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- The query the shared board itself runs: "everything open, across every module, for this tenant."
CREATE INDEX idx_control_exceptions_open ON control_exceptions (tenant_id, status) WHERE status = 'OPEN';
-- The query SCM's resolve path runs: "is there an open exception already tied to this specific invoice?"
CREATE INDEX idx_control_exceptions_entity ON control_exceptions (tenant_id, related_entity_type, related_entity_id);