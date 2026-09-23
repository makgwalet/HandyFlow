package za.co.handyflow.platform.compliancetender.dto;

import jakarta.validation.constraints.NotBlank;

/** Creates a NEW VERSION of the requirement with this code — the existing version is never edited in place. See ComplianceRequirement.newVersion's own Javadoc. */
public record UpdateComplianceRequirementRequest(
        @NotBlank String name, String appliesTo, String evidenceType, boolean required
) {}
