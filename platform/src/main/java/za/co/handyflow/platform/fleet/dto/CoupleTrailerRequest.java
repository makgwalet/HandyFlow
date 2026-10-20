package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CoupleTrailerRequest(
        @NotNull UUID hostVehicleId
) {}