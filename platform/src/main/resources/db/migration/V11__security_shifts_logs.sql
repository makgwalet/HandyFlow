-- Shifts: guard assigned to site for a time window
CREATE TABLE security_shifts (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL,
    site_id         UUID        NOT NULL,
    guard_id        UUID        NOT NULL,
    start_at        TIMESTAMP   NOT NULL,
    end_at          TIMESTAMP   NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    notes           TEXT,
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMP,
    deleted_by      UUID,
    version         BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_security_shifts PRIMARY KEY (id),
    CONSTRAINT fk_shift_site
        FOREIGN KEY (site_id)  REFERENCES security_sites(id),
    CONSTRAINT fk_shift_guard
        FOREIGN KEY (guard_id) REFERENCES security_guards(id),
    CONSTRAINT chk_shift_status CHECK (
        status IN ('SCHEDULED','ACTIVE','COMPLETED','MISSED','CANCELLED')
    ),
    -- WHY check? Shift end must be after start — catches data entry errors
    CONSTRAINT chk_shift_dates CHECK (end_at > start_at)
);

-- Checkpoint logs: every QR scan creates one record
CREATE TABLE security_checkpoint_logs (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL,
    checkpoint_id   UUID        NOT NULL,
    guard_id        UUID        NOT NULL,
    shift_id        UUID,               -- nullable: guard may scan outside shift
    scanned_at      TIMESTAMP   NOT NULL DEFAULT now(),
    latitude        DECIMAL(10,7),      -- GPS at scan time (nullable)
    longitude       DECIMAL(10,7),
    notes           TEXT,

    CONSTRAINT pk_checkpoint_logs PRIMARY KEY (id),
    CONSTRAINT fk_log_checkpoint
        FOREIGN KEY (checkpoint_id) REFERENCES security_checkpoints(id),
    CONSTRAINT fk_log_guard
        FOREIGN KEY (guard_id) REFERENCES security_guards(id),
    CONSTRAINT fk_log_shift
        FOREIGN KEY (shift_id) REFERENCES security_shifts(id) ON DELETE SET NULL
);

CREATE INDEX idx_shifts_tenant  ON security_shifts(tenant_id)         WHERE deleted_at IS NULL;
CREATE INDEX idx_shifts_guard   ON security_shifts(guard_id, start_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_shifts_site    ON security_shifts(site_id, start_at)  WHERE deleted_at IS NULL;
CREATE INDEX idx_logs_checkpoint ON security_checkpoint_logs(checkpoint_id, scanned_at);
CREATE INDEX idx_logs_guard      ON security_checkpoint_logs(guard_id, scanned_at);
CREATE INDEX idx_logs_shift      ON security_checkpoint_logs(shift_id) WHERE shift_id IS NOT NULL;