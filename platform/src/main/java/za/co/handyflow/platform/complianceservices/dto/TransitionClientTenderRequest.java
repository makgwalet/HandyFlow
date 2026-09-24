package za.co.handyflow.platform.complianceservices.dto;

import jakarta.validation.constraints.NotBlank;

public record TransitionClientTenderRequest(@NotBlank String newStatus) {}
