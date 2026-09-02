package za.co.handyflow.platform.bookkeeping.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record InviteBkPortalUserRequest(
        @NotNull UUID clientId,
        @NotBlank @Email String email
) {}
