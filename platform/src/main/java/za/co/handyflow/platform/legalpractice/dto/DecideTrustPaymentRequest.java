package za.co.handyflow.platform.legalpractice.dto;

import jakarta.validation.constraints.NotBlank;

public record DecideTrustPaymentRequest(@NotBlank String reason) {}
