package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** revokeTransmittalLinks defaults to true: disabling an account also cuts every transmittal link issued to them. */
public record DisableCollaboratorRequest(
        @NotBlank(message = "A reason is required")
        @Size(max = 500, message = "Reason must not exceed 500 characters") String reason,
        Boolean revokeTransmittalLinks
) {}
