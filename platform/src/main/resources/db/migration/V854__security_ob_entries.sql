-- Daily Occurrence Book: manual entries (notes, handovers). Append-only: corrections are new entries.
CREATE TABLE security_ob_entries (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL,
    site_id       UUID NOT NULL,
    guard_id      UUID,
    entered_by    UUID NOT NULL,
    entry_type    VARCHAR(20) NOT NULL CHECK (entry_type IN ('NOTE','HANDOVER','CORRECTION')),
    text          TEXT NOT NULL CHECK (char_length(text) BETWEEN 3 AND 2000),
    recorded_at   TIMESTAMPTZ NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    client_ref    VARCHAR(64)
);
CREATE INDEX idx_ob_entries_site_time ON security_ob_entries (tenant_id, site_id, recorded_at);
CREATE UNIQUE INDEX uq_ob_entries_client_ref ON security_ob_entries (tenant_id, entered_by, client_ref) WHERE client_ref IS NOT NULL;

-- Append-only enforced in the database, not only in code.
CREATE OR REPLACE FUNCTION security_ob_entries_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Occurrence Book entries cannot be changed or deleted; add a CORRECTION entry';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_ob_entries_immutable BEFORE UPDATE OR DELETE ON security_ob_entries
    FOR EACH ROW EXECUTE FUNCTION security_ob_entries_immutable();
