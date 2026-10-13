package za.co.handyflow.platform.debtcollection.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** priority == null clears the override. A reason is always required. */
public record PriorityOverrideRequest(@Min(0) @Max(100) Integer priority, @NotBlank String reason) {}
