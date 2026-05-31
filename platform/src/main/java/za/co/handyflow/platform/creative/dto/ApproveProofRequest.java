package za.co.handyflow.platform.creative.dto;

import jakarta.validation.constraints.NotBlank;

public record ApproveProofRequest(
        @NotBlank String clientName,
        String clientEmail
) {}