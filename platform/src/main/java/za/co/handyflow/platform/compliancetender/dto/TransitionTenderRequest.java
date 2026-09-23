package za.co.handyflow.platform.compliancetender.dto;

import jakarta.validation.constraints.NotBlank;

public record TransitionTenderRequest(@NotBlank String newStatus) {}
