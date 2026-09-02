package za.co.handyflow.platform.facilities.dto;

import jakarta.validation.constraints.NotBlank;

public record RevokeCertificateRequest(@NotBlank String reason) {}
