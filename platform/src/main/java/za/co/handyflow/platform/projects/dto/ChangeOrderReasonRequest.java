package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Used for reject, withdraw, client dispute and dispute resolution — each needs a stated reason. */
public record ChangeOrderReasonRequest(
        @NotBlank(message = "A reason is required")
        @Size(max = 2000, message = "Reason must not exceed 2 000 characters")
        String reason
) {}