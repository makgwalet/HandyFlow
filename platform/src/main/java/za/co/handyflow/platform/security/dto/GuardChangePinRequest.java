package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record GuardChangePinRequest(
        @NotBlank String currentPin,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "New PIN must be exactly 6 digits")
        String newPin
) {}