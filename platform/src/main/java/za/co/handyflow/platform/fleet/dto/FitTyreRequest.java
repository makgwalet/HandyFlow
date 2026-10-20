package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;
import java.util.UUID;

/**
 * Fit an in-stock tyre. {@code position} is a short code such as FL, FR, RL, RR, A2LO or SPARE;
 * a vehicle can have one tyre per position (any number of spares). {@code odometer} defaults to the
 * vehicle's current reading and cannot exceed it; give a lower one to back-date a fitting.
 */
public record FitTyreRequest(
        @NotNull UUID vehicleId,
        @NotBlank String position,
        @PositiveOrZero Integer odometer,
        Instant fittedAt,
        UUID workOrderId
) {}