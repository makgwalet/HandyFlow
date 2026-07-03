package za.co.handyflow.platform.earthmoving.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DeploymentResponse(
        UUID id,
        UUID assetId,
        String siteName,
        String clientName,
        String contactName,
        String contactPhone,
        LocalDate plannedStartDate,
        LocalDate plannedEndDate,
        Instant deployedAt,
        Instant returnedAt,
        String endReason,
        String notes
) {}
