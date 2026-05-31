package za.co.handyflow.platform.identity.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record InviteUserRequest(
        @Email @NotBlank String email,
        @NotBlank        String firstName,
        @NotBlank        String lastName,
                         String jobTitle,
                         String department,
                         UUID   roleId      // which role to assign on acceptance
) {}
