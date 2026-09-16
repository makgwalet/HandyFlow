package za.co.handyflow.platform.security.dto;

import java.time.Instant;
import java.util.UUID;

public record PostOrderAcknowledgementResponse(
        UUID id, UUID postOrderId, int version, UUID guardId, String guardName,
        Instant acknowledgedAt, String deviceHardwareId, boolean acknowledgedOffline, Instant syncedAt
) {}
