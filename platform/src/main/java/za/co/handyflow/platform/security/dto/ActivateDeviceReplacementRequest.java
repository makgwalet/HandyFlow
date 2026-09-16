package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ActivateDeviceReplacementRequest(
        @NotBlank @Pattern(regexp = "\\d{6}", message = "Code must be exactly 6 digits") String code,
        @NotBlank String newDeviceHardwareId,
        String newDeviceName
) {}
