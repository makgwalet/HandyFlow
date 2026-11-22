-- A supervisor signs a site's Occurrence Book for a day. One signature per site per day; never changed or removed.
CREATE TABLE security_ob_signoffs (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  UUID NOT NULL,
    site_id    UUID NOT NULL,
    day        DATE NOT NULL,
    signed_by  UUID NOT NULL,
    signed_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    note       TEXT CHECK (note IS NULL OR char_length(note) <= 500),
    CONSTRAINT uq_ob_signoff_day UNIQUE (tenant_id, site_id, day)
);

CREATE OR REPLACE FUNCTION security_ob_signoffs_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'A signed Occurrence Book day cannot be changed or unsigned; add a CORRECTION entry';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_ob_signoffs_immutable BEFORE UPDATE OR DELETE ON security_ob_signoffs
    FOR EACH ROW EXECUTE FUNCTION security_ob_signoffs_immutable();
