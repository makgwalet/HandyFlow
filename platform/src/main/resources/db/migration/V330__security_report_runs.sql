-- One row each time a security report is viewed or downloaded, so the Reports page can show when each report
-- was last generated and by whom. Append-only; the report data itself is never stored here.
CREATE TABLE security_report_runs (
    id                UUID         PRIMARY KEY,
    tenant_id         UUID         NOT NULL,
    report_key        VARCHAR(40)  NOT NULL,
    period            VARCHAR(7)   NOT NULL,
    subject           VARCHAR(200),
    format            VARCHAR(10)  NOT NULL,
    generated_by_id   UUID,
    generated_by_name VARCHAR(200),
    generated_at      TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_security_report_runs_tenant_time ON security_report_runs (tenant_id, generated_at DESC);
CREATE INDEX idx_security_report_runs_tenant_key  ON security_report_runs (tenant_id, report_key, generated_at DESC);
