package za.co.handyflow.platform.complianceservices.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record CreateClientTenderRequirementRequest(
        UUID clientRequirementId, @NotBlank String description, String source
) {}
