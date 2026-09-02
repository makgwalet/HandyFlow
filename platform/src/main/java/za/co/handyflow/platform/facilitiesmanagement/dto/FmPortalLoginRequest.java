package za.co.handyflow.platform.facilitiesmanagement.dto;

import jakarta.validation.constraints.NotBlank;

public record FmPortalLoginRequest(
        @NotBlank String email,
        @NotBlank String password
) {}
