-- One row per tenant per day on which the internal audit dashboard's headline figures were looked at, so the dashboard can say
-- how they have moved. The figures are counted, not derived, at the time: nothing is back-filled, and a day nobody opened the
-- dashboard has no row.
CREATE TABLE audit_dashboard_snapshots (
    tenant_id       UUID    NOT NULL,
    taken_on        DATE    NOT NULL,
    engagements     INTEGER NOT NULL,
    open_findings   INTEGER NOT NULL,
    overdue_actions INTEGER NOT NULL,
    high_risk_areas INTEGER NOT NULL,
    PRIMARY KEY (tenant_id, taken_on)
);
