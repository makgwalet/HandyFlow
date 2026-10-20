package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Completes the order. {@code odometer} is required for SERVICE orders (it resets the service
 * interval) and cannot be lower than the vehicle's current reading. {@code completedAt} defaults to now.
 */
public record CompleteWorkOrderRequest(
        @PositiveOrZero Integer odometer,
        @Size(max = 100) String invoiceRef,
        @Size(max = 4000) String completionNotes,
        @PositiveOrZero Integer nextServiceKm,
        Instant completedAt
) {}