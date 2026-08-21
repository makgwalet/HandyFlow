package za.co.handyflow.platform.hr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HrPortalRegisterRequest(
        @NotBlank String inviteToken,
        @NotBlank @Size(min = 8) String password,
        @NotBlank String fullName
) {}
