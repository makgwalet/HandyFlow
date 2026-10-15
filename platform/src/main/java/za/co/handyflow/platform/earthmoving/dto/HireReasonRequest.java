package za.co.handyflow.platform.earthmoving.dto;

import jakarta.validation.constraints.NotBlank;

public record HireReasonRequest(@NotBlank String reason) {}
