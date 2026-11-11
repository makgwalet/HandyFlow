package za.co.handyflow.platform.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VoidCostEntryRequest(
        @NotBlank @Size(max = 500) String reason
) {}