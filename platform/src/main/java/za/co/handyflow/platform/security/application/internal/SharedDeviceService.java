package za.co.handyflow.platform.security.application.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.SecurityDevice;
import za.co.handyflow.platform.security.domain.model.SecurityDevice.DeviceType;
import za.co.handyflow.platform.security.domain.repository.GuardTokenRepository;
import za.co.handyflow.platform.security.domain.repository.SecurityDeviceRepository;
import za.co.handyflow.platform.security.domain.repository.SiteRepository;
import za.co.handyflow.platform.security.dto.RegisterSharedDeviceRequest;
import za.co.handyflow.platform.security.dto.SharedDeviceResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

/** Supervisor-side management of shared site devices (a phone or tablet at a post used by whoever is on duty). */
@Slf4j
@Service
@RequiredArgsConstructor
public class SharedDeviceService {

    private final SecurityDeviceRepository deviceRepository;
    private final GuardTokenRepository tokenRepository;
    private final SiteRepository siteRepository;

    @Transactional
    public SharedDeviceResponse register(TenantId tenantId, RegisterSharedDeviceRequest req) {
        String hw = req.deviceHardwareId().trim();
        if (req.siteId() != null && siteRepository.findActiveById(tenantId, req.siteId()).isEmpty()) {
            throw new ResourceNotFoundException("Site", req.siteId().toString());
        }
        var existing = deviceRepository.findByTenantIdAndDeviceHardwareId(tenantId, hw);
        if (existing.isPresent()) {
            SecurityDevice d = existing.get();
            // Never repurpose a guard's personal phone as a shared device.
            if (d.getDeviceType() != DeviceType.SHARED_SITE_DEVICE) {
                throw new HandyFlowException("This device is already registered as a guard's personal device.",
                        HttpStatus.CONFLICT, "DEVICE_IS_PERSONAL");
            }
            if (!d.isActive()) { d.activate(); deviceRepository.save(d); }
            return toResponse(d);
        }
        SecurityDevice d = SecurityDevice.create(tenantId, req.siteId(), hw, req.deviceName(), DeviceType.SHARED_SITE_DEVICE);
        deviceRepository.save(d);
        log.info("[Security] Shared device registered id={} site={} tenant={}", d.getId(), req.siteId(), tenantId);
        return toResponse(d);
    }

    @Transactional(readOnly = true)
    public List<SharedDeviceResponse> list(TenantId tenantId, UUID siteId) {
        return deviceRepository.findAllByTenant(tenantId).stream()
                .filter(d -> d.getDeviceType() == DeviceType.SHARED_SITE_DEVICE)
                .filter(d -> siteId == null || siteId.equals(d.getSiteId()))
                .map(SharedDeviceService::toResponse)
                .toList();
    }

    /** Pulls a shared device out of service (lost or stolen tablet) and ends any session open on it. */
    @Transactional
    public int revoke(TenantId tenantId, UUID id) {
        SecurityDevice d = deviceRepository.findAllByTenant(tenantId).stream()
                .filter(x -> x.getId().equals(id) && x.getDeviceType() == DeviceType.SHARED_SITE_DEVICE)
                .findFirst().orElseThrow(() -> new ResourceNotFoundException("Shared device", id.toString()));
        d.revoke();
        deviceRepository.save(d);
        int ended = tokenRepository.revokeActiveOnDevice(tenantId, d.getDeviceHardwareId(), Instant.now(),
                "Shared device revoked");
        log.info("[Security] Shared device revoked id={} sessionsEnded={} tenant={}", id, ended, tenantId);
        return ended;
    }

    private static SharedDeviceResponse toResponse(SecurityDevice d) {
        return new SharedDeviceResponse(d.getId(), d.getSiteId(), d.getDeviceHardwareId(), d.getDeviceName(),
                d.getStatus().name(), d.getLastSeenAt());
    }
}
