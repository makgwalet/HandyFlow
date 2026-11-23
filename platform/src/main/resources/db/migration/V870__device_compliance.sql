-- Device compliance (first slice of device management). A company states what a guard phone must be like; phones report their
-- posture; a report lists the phones that fall short. Report only: nothing here blocks a phone or signs a guard out.

-- One policy per company. A column left empty / false means "not required".
CREATE TABLE IF NOT EXISTS security_device_policy (
    tenant_id           UUID        NOT NULL REFERENCES tenants(id),
    min_app_version     VARCHAR(30),
    min_os_major        INTEGER,
    require_screen_lock BOOLEAN     NOT NULL DEFAULT false,
    block_rooted        BOOLEAN     NOT NULL DEFAULT false,
    updated_by          UUID,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_security_device_policy PRIMARY KEY (tenant_id),
    CONSTRAINT chk_device_policy_os CHECK (min_os_major IS NULL OR (min_os_major BETWEEN 1 AND 99)),
    CONSTRAINT chk_device_policy_app CHECK (min_app_version IS NULL OR min_app_version ~ '^[0-9]+(\.[0-9]+){0,3}$')
);

-- What the phone last said about itself. NULL = never told us (not the same as "no").
ALTER TABLE security_devices ADD COLUMN IF NOT EXISTS os_version         VARCHAR(30);
ALTER TABLE security_devices ADD COLUMN IF NOT EXISTS screen_lock_on     BOOLEAN;
ALTER TABLE security_devices ADD COLUMN IF NOT EXISTS rooted             BOOLEAN;
ALTER TABLE security_devices ADD COLUMN IF NOT EXISTS posture_reported_at TIMESTAMPTZ;

DO $$ BEGIN
    IF to_regproc('hf_rls_apply') IS NOT NULL THEN PERFORM hf_rls_apply(); END IF;
END $$;
