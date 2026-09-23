package za.co.handyflow.platform.compliancetender.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record CreateTenderRequirementRequest(
        UUID complianceRequirementId, @NotBlank String description, String source
) {}
