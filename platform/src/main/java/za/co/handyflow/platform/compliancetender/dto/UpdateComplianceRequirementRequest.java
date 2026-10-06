package za.co.handyflow.platform.compliancetender.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Creates a NEW VERSION of the requirement with this code — the existing version is never edited in place. See ComplianceRequirement.newVersion's own Javadoc. */
public record UpdateComplianceRequirementRequest(
        @NotBlank String name, String appliesTo, String evidenceType, boolean required,
        @Size(max = 20) String satisfiedByAuthority, @Size(max = 60) String satisfiedByRegistrationType
) {
    /** Without the rule fields: the new version KEEPS the existing registration rule (null = unchanged; a blank value clears it). */
    public UpdateComplianceRequirementRequest(String name, String appliesTo, String evidenceType, boolean required) {
        this(name, appliesTo, evidenceType, required, null, null);
    }
}
