-- V279__guard_device_lockdown.sql
-- Guard device enrollment lockdown, per the product owner's own explicit
-- decision: keep supervisor-controlled enrollment (not a missing
-- feature, a genuine security control for a regulated security
-- context), add device states/history, and design a controlled,
-- supervisor-authorized device-replacement workflow rather than open
-- self-registration.
--
-- Also fixes a separate, more foundational finding made while building
-- this: Guard.registeredDeviceId was set at enrollment but never
-- actually checked at login — GuardAuthService.login() never compared
-- req.deviceId() against it. "Revoked devices cannot authenticate" is
-- meaningless without this enforcement existing first; see
-- GuardAuthService's own updated class comment for the fuller story.

ALTER TABLE security_devices
    DROP CONSTRAINT security_devices_status_check;

ALTER TABLE security_devices
    ADD CONSTRAINT security_devices_status_check
        CHECK (status IN ('PENDING', 'ACTIVE', 'REVOKED', 'LOST', 'BLOCKED', 'REPLACED', 'DECOMMISSIONED'));

-- FIX (guard device history): SecurityDevice already covers
-- SHARED_SITE_DEVICE (siteId, no owning guard) but PERSONAL_GUARD_DEVICE
-- rows had no direct guardId of their own — Guard.registeredDeviceId was
-- the only link, a bare string with no row-level history. Nullable —
-- only ever set for PERSONAL_GUARD_DEVICE rows.
ALTER TABLE security_devices
    ADD COLUMN guard_id UUID REFERENCES security_guards (id);

CREATE INDEX idx_security_devices_guard ON security_devices (tenant_id, guard_id) WHERE guard_id IS NOT NULL;

-- The supervisor-authorized replacement workflow itself: supervisor
-- generates a short-lived code, guard enters/scans it on the new device,
-- backend verifies guard + code + expiry + old-device status before
-- revoking the old device and activating the new one.
CREATE TABLE guard_device_activation_codes (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL REFERENCES tenants (id),
    guard_id      UUID NOT NULL REFERENCES security_guards (id),
    code          VARCHAR(10) NOT NULL,
    expires_at    TIMESTAMPTZ NOT NULL,
    used_at       TIMESTAMPTZ,
    used_by_device_hardware_id VARCHAR(200),
    created_by    UUID,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_device_activation_codes_guard ON guard_device_activation_codes (tenant_id, guard_id) WHERE used_at IS NULL;
-- Lookup during activation is by code alone (the guard doesn't have a
-- guardId to hand — they only have the code) so this needs its own index,
-- not just the composite above.
CREATE INDEX idx_device_activation_codes_code ON guard_device_activation_codes (tenant_id, code) WHERE used_at IS NULL;
