package za.co.handyflow.platform.legalcompliance.dto;

import jakarta.validation.constraints.NotBlank;

public record MarkNonCompliantRequest(@NotBlank String notes) {}
