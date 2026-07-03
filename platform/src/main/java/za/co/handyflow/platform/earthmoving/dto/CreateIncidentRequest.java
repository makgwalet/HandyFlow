package za.co.handyflow.platform.earthmoving.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateIncidentRequest(
        @NotNull UUID assetId,
        @NotBlank String type,      // BREAKDOWN | ACCIDENT | THEFT | FIRE | ROLLOVER | NEAR_MISS | FUEL_SPILL | OTHER
        @NotBlank String severity,  // LOW | MEDIUM | HIGH | CRITICAL
        @NotBlank String title,
        String description,
        String operatorName,
        String siteName,
        Double latitude,
        Double longitude
) {}