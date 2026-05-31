package za.co.handyflow.platform.contracting.dto;

import jakarta.validation.constraints.NotBlank;

public record SignContractRequest(
        @NotBlank String otpCode,
        String signatureData
) {}