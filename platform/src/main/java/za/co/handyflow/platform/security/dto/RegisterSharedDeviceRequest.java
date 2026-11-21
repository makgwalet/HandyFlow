package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record RegisterSharedDeviceRequest(
        @NotBlank @Size(max = 200) String deviceHardwareId,
        @Size(max = 100) String deviceName,
        UUID siteId) {}
