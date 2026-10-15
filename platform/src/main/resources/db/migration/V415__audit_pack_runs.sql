-- A14. Each time the financial audit pack is produced, what was asked for and a fingerprint of what came out. The pack itself is not
-- stored: it is rebuilt from the ledger, and "verify" rebuilds it again and compares fingerprints, so a pack handed to an auditor can be
-- shown to match (or not match) the books as they stand now.
CREATE TABLE audit_pack_runs (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID         NOT NULL,
    engagement_id UUID,
    period_from   DATE         NOT NULL,
    period_to     DATE         NOT NULL,
    generated_by  UUID         NOT NULL,
    generated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    pack_sha256   CHAR(64)     NOT NULL,
    file_count    INT          NOT NULL,
    manifest      TEXT         NOT NULL,
    CHECK (period_to >= period_from)
);
CREATE INDEX idx_audit_pack_runs_tenant ON audit_pack_runs (tenant_id, generated_at DESC);
