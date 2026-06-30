package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;

public record RegisterVehicleRequest(
        @NotBlank String vehicleType,   // PRINCIPAL_CAR | LEAD_CAR | FOLLOW_CAR
        @NotBlank String registration,
        String makeModel,
        boolean armored,
        String notes
) {}
