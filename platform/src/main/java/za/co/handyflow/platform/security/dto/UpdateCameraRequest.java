package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCameraRequest(
        @NotBlank @Size(max = 150) String name,
        String provider,
        String connectionConfigJson,
        String notes
) {}
