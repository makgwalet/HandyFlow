package za.co.handyflow.platform.complianceservices.dto;

import jakarta.validation.constraints.NotBlank;

/** Creates a NEW VERSION of the requirement with this code — the existing version is never edited in place. */
public record UpdateClientComplianceRequirementRequest(
        @NotBlank String name, String appliesTo, String evidenceType, boolean required
) {}
