package za.co.handyflow.platform.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A scan at the door must never fail on a long free-text field, so every field is bounded to its column. */
public record CheckInRequest(
        @NotBlank @Size(max = 100) String qrCode,
        @Size(max = 200) String location,
        @Size(max = 100) String scanDevice
) {}
