package za.co.handyflow.platform.insurancebrokerage.dto;

import jakarta.validation.constraints.NotBlank;

public record CancelInsBrokPolicyRequest(
        @NotBlank String reason
) {}
