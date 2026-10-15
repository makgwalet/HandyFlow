package za.co.handyflow.platform.earthmoving.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdatePlanRequest(@NotBlank String name, @NotNull Integer intervalValue, Integer leadValue, String priority,
                                Boolean blocksRelease, Boolean active) {}
