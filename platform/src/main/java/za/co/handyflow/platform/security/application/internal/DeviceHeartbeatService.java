package za.co.handyflow.platform.security.application.internal;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.SecurityDevice;
import za.co.handyflow.platform.security.domain.repository.DeviceSessionRepository;
import za.co.handyflow.platform.security.domain.repository.SecurityDeviceRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

/** Records that a phone is alive: last seen, battery and app version. Feeds the fleet health view. */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceHeartbeatService {

    private final SecurityDeviceRepository devices;
    private final DeviceSessionRepository sessions;

    /** The phone's own check-in. A phone that belongs to another guard answers as if it did not exist. */
    @Transactional
    public void report(TenantId tenantId, UUID guardId, String deviceHardwareId, Integer battery, String appVersion) {
        SecurityDevice d = devices.findByTenantIdAndDeviceHardwareId(tenantId, deviceHardwareId)
                .orElseThrow(() -> new ResourceNotFoundException("SecurityDevice", deviceHardwareId));
        switch (HeartbeatRules.access(d.getGuardId(), d.isActive(), guardId)) {
            case NOT_YOURS -> throw new ResourceNotFoundException("SecurityDevice", deviceHardwareId);
            case INACTIVE -> throw new HandyFlowException("This device is " + d.getStatus() + " and is out of service",
                    HttpStatus.FORBIDDEN, "DEVICE_INACTIVE");
            case OK -> apply(d, battery, appVersion);
        }
    }

    /** A location ping on an open session also counts as a check-in. Never throws: the ping matters more. */
    @Transactional
    public void reportForSession(TenantId tenantId, UUID guardId, UUID sessionId, Integer battery, String appVersion) {
        try {
            sessions.findById(sessionId)
                    .filter(s -> s.getTenantId().equals(tenantId) && s.getGuardId().equals(guardId))
                    .flatMap(s -> devices.findById(s.getDeviceId()))
                    .filter(d -> d.getTenantId().equals(tenantId))
                    .ifPresent(d -> apply(d, battery, appVersion));
        } catch (RuntimeException e) {
            log.warn("[Security] Device check-in from ping failed session={}: {}", sessionId, e.getMessage());
        }
    }

    private void apply(SecurityDevice d, Integer battery, String appVersion) {
        Instant now = Instant.now();
        Integer b = HeartbeatRules.cleanBattery(battery);
        String v = HeartbeatRules.cleanVersion(appVersion);
        if (!HeartbeatRules.shouldWrite(d.getLastSeenAt(), now, d.getBatteryPct(), b, d.getAppVersion(), v)) return;
        d.reportHeartbeat(now, b, v);
        devices.save(d);
    }
}
