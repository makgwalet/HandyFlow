package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record OpenSessionRequest(
        @NotBlank String deviceHardwareId,
        @NotNull UUID guardId,
        boolean pinVerified,
        Double faceMatchConfidence,   // null if face not captured
        Boolean geofenceOk            // null if site has no geofence
) {}
