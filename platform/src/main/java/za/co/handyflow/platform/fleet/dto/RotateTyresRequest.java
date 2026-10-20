package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Rotate several tyres on one vehicle atomically — including swaps (A→B and B→A). Tyres not listed stay put. */
public record RotateTyresRequest(
        @NotEmpty @Size(max = 30) List<@Valid Move> moves,
        @PositiveOrZero Integer odometer,
        Instant rotatedAt,
        UUID workOrderId
) {
    public record Move(@NotNull UUID tyreId, @NotBlank String toPosition) {}
}