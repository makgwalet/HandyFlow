package za.co.handyflow.platform.events.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The NEW ticket holder. Email is required — the replacement ticket is sent there. */
public record TransferGuestRequest(
        @NotBlank @Size(max = 200) String fullName,
        @NotBlank @Email @Size(max = 254) String email,
        @Size(max = 50) String phone,
        @Size(max = 200) String company,
        @Size(max = 500) String dietaryRequirements,
        @Size(max = 500) String reason
) {}