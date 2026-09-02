package za.co.handyflow.platform.bookkeeping.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BkPortalRegisterRequest(
        @NotBlank String inviteToken,
        @NotBlank @Size(min = 8) String password,
        @NotBlank String fullName
) {}
