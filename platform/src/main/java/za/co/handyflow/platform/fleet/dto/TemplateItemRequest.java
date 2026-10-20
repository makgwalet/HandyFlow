package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.fleet.domain.model.IssueSeverity;

/** One checklist question. Omitted flags default to: required=true, photoRequiredOnFail=false, severity=MEDIUM. */
public record TemplateItemRequest(
        @NotBlank @Size(max = 100) String section,
        @NotBlank @Size(max = 255) String label,
        Boolean required,
        Boolean photoRequiredOnFail,
        IssueSeverity severity
) {}