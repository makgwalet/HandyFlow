package za.co.handyflow.platform.accountant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PortalRegisterRequest(
        @NotBlank String inviteToken,
        @NotBlank @Size(min = 8) String password,
        @NotBlank String fullName
) {
}