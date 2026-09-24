package za.co.handyflow.platform.complianceservices.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateClientComplianceRequirementRequest(
        @NotBlank String code, @NotBlank String name, String appliesTo, String evidenceType, boolean required
) {}
