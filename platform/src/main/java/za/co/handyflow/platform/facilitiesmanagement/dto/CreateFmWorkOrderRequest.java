package za.co.handyflow.platform.facilitiesmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateFmWorkOrderRequest(
        @NotNull UUID clientId, @NotNull UUID siteId, UUID assetId, @NotBlank String category,
        String priority, @NotBlank String description, String reportedBy, LocalDate scheduledDate
) {}
