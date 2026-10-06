package za.co.handyflow.platform.compliancetender.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateComplianceRequirementRequest(
        @NotBlank String code, @NotBlank String name, String appliesTo, String evidenceType, boolean required,
        @Size(max = 20) String satisfiedByAuthority, @Size(max = 60) String satisfiedByRegistrationType
) {
    /** Without a registration rule (the shape before business readiness). */
    public CreateComplianceRequirementRequest(String code, String name, String appliesTo, String evidenceType, boolean required) {
        this(code, name, appliesTo, evidenceType, required, null, null);
    }
}
