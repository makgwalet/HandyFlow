package za.co.handyflow.platform.hr.dto;

import jakarta.validation.constraints.NotBlank;

public record HrPortalLoginRequest(
        @NotBlank String email,
        @NotBlank String password
) {}