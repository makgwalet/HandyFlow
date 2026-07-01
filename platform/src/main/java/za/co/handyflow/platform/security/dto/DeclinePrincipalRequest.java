package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;

public record DeclinePrincipalRequest(
        @NotBlank String reason,
        String sensitiveDetail   // encrypted before storage
) {}
