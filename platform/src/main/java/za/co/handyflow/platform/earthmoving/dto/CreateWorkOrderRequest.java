package za.co.handyflow.platform.earthmoving.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateWorkOrderRequest(
        @NotNull UUID assetId,
        @NotBlank String title,
        String description,
        @NotBlank String type,
        String priority,          // LOW | NORMAL | HIGH | URGENT (default NORMAL)
        LocalDate dueDate,
        UUID assignedToUserId,
        UUID incidentId,
        Boolean blocksRelease     // default true
) {}
