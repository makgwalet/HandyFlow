package za.co.handyflow.platform.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Used for reject and reopen — both need a recorded reason. */
public record BudgetReasonRequest(
        @NotBlank @Size(max = 500) String reason
) {}