package za.co.handyflow.platform.security.dto;

import java.time.Instant;
import java.util.UUID;

public record SharedDeviceResponse(UUID id, UUID siteId, String deviceHardwareId, String deviceName,
                                   String status, Instant lastSeenAt) {}
