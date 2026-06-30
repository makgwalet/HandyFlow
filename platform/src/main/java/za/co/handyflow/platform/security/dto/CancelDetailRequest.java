package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;

public record CancelDetailRequest(
        @NotBlank String reason
) {}
