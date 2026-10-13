package za.co.handyflow.platform.debtcollection.dto;

import jakarta.validation.constraints.NotBlank;

public record DismissSuspenseRequest(@NotBlank String reason) {}
