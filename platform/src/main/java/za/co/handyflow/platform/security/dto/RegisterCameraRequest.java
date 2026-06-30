package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record RegisterCameraRequest(
        @NotNull UUID siteId,
        @NotBlank @Size(max = 150) String name,
        String provider,            // HIKVISION_CLOUD | DAHUA_CLOUD | ONVIF | RTSP_GENERIC | OTHER | NONE
        String connectionConfigJson, // vendor-specific config, raw JSON string
        String notes
) {}
