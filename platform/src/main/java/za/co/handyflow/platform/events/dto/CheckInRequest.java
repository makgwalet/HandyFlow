package za.co.handyflow.platform.events.dto;

import jakarta.validation.constraints.NotBlank;

public record CheckInRequest(
        @NotBlank String qrCode,
        String location,
        String scanDevice
) {}