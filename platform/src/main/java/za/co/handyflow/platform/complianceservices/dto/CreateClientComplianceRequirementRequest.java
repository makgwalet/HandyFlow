package za.co.handyflow.platform.complianceservices.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateClientComplianceRequirementRequest(
        @NotBlank String code, @NotBlank String name, String appliesTo, String evidenceType, boolean required,
        @Size(max = 20) String satisfiedByAuthority, @Size(max = 60) String satisfiedByRegistrationType
) {
    /** Without a registration rule (the shape before business readiness). */
    public CreateClientComplianceRequirementRequest(String code, String name, String appliesTo, String evidenceType, boolean required) {
        this(code, name, appliesTo, evidenceType, required, null, null);
    }
}
