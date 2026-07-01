package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;

public record CreatePrincipalVettingRequest(
        @NotBlank String vettingType   // SANCTIONS_SCREENING | PEP_CHECK | ADVERSE_MEDIA | ...
) {}
