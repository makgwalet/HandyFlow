package za.co.handyflow.platform.creative.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SendProofRequest(
        @Email @NotBlank String email,
        String message   // optional custom message in email
) {}
