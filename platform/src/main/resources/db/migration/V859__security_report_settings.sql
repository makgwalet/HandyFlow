-- Who clients should contact with questions about a monthly service report. One row per company. A report is not emailed until it is set,
-- so no client is ever sent a report that appears to accept replies nobody reads.
CREATE TABLE IF NOT EXISTS security_report_settings (
    tenant_id     UUID PRIMARY KEY,
    contact_name  VARCHAR(100),
    contact_email VARCHAR(254) NOT NULL,
    contact_phone VARCHAR(40),
    updated_by    UUID,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_report_contact_email CHECK (contact_email ~ '^[^[:space:]@<>,;"'']+@[^[:space:]@<>,;"''.]+(\.[^[:space:]@<>,;"''.]+)+$')
);
COMMENT ON TABLE security_report_settings IS 'Monitored contact shown in every emailed monthly service report. Required before a report can be sent.';
