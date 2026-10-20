package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.fleet.domain.model.IssueSeverity;

import java.util.UUID;

public record CreateIssueRequest(
        @NotNull UUID vehicleId,
        @NotBlank @Size(max = 255) String title,
        String description,
        IssueSeverity severity     // defaults to MEDIUM
) {}