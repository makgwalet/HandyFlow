package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReverseCostEntryRequest(
        @NotBlank(message = "A reason is required to reverse a ledger entry")
        @Size(max = 400, message = "Reason must not exceed 400 characters")
        String reason
) {}