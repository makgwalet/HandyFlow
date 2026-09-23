package za.co.handyflow.platform.compliancetender.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateComplianceRequirementRequest(
        @NotBlank String code, @NotBlank String name, String appliesTo, String evidenceType, boolean required
) {}
