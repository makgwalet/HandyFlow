package za.co.handyflow.platform.bookkeeping.dto;

import jakarta.validation.constraints.NotBlank;

public record BkPortalLoginRequest(
        @NotBlank String email,
        @NotBlank String password
) {}
