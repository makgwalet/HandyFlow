package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.SecurityDevice;
import za.co.handyflow.platform.security.domain.model.SecurityDevice.DeviceType;
import za.co.handyflow.platform.security.domain.repository.GuardRepository;
import za.co.handyflow.platform.security.domain.repository.GuardTokenRepository;
import za.co.handyflow.platform.security.domain.repository.SecurityDeviceRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** Lost / revoke / reinstate for guards' personal phones (shared site devices have their own revoke). */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceManagementService {

    private final SecurityDeviceRepository deviceRepository;
    private final GuardTokenRepository     tokenRepository;
    private final GuardRepository          guardRepository;

    /** @return number of sessions ended on the phone */
    @Transactional
    public int apply(TenantId tenantId, UUID deviceId, DeviceStateRules.Action action, UUID actorId) {
        SecurityDevice d = deviceRepository.findAllByTenant(tenantId).stream()
                .filter(x -> x.getId().equals(deviceId) && x.getDeviceType() == DeviceType.PERSONAL_GUARD_DEVICE)
                .findFirst().orElseThrow(() -> new ResourceNotFoundException("Device", deviceId.toString()));

        String status = d.getStatus().name();
        if (!DeviceStateRules.allowed(action, status)) {
            throw new HandyFlowException("A device that is " + status + " cannot be changed that way.",
                    HttpStatus.CONFLICT, "DEVICE_STATE_NOT_ALLOWED");
        }
        if (action == DeviceStateRules.Action.REINSTATE) {
            var guard = d.getGuardId() == null ? null
                    : guardRepository.findByIdAndTenantForAuth(d.getGuardId(), tenantId).orElse(null);
            if (guard == null || !DeviceStateRules.isGuardsCurrentDevice(guard.getRegisteredDeviceId(), d.getDeviceHardwareId())) {
                throw new HandyFlowException("This is no longer the guard's current device. Enrol the guard on it again instead.",
                        HttpStatus.CONFLICT, "DEVICE_NOT_CURRENT");
            }
        }

        switch (action) {
            case LOST -> d.markLost();
            case REVOKE -> d.revoke();
            case REINSTATE -> d.activate();
        }
        deviceRepository.save(d);

        int ended = DeviceStateRules.endsSessions(action)
                ? tokenRepository.revokeActiveOnDevice(tenantId, d.getDeviceHardwareId(), Instant.now(),
                        "Device " + action.target.toLowerCase())
                : 0;
        log.info("[Security] Device {} -> {} by user={} guard={} sessionsEnded={} tenant={}",
                deviceId, action.target, actorId, d.getGuardId(), ended, tenantId);
        return ended;
    }
}
