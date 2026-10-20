package za.co.handyflow.platform.fleet.dto;

import java.time.Instant;
import java.util.UUID;

public record BatteryInstallationResponse(
        UUID id,
        UUID vehicleId,
        String registration,
        String position,
        Instant installedAt,
        Integer installedOdometer,
        Instant removedAt,
        Integer removedOdometer,
        Integer km,
        String removalReason,
        UUID installWorkOrderId,
        UUID removeWorkOrderId
) {}