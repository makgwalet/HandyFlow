package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Puts a tanker and driver on a load and splits the litres across compartments (must add up to the load). */
public record AllocationRequest(
        @NotNull UUID tankerId,
        @NotNull UUID driverEmployeeId,
        @NotEmpty @Valid List<CompartmentLoad> loads,
        // Only needed when the eligibility check has review points: why an authorised person is allocating it anyway.
        String reviewReason
) {
    public AllocationRequest(UUID tankerId, UUID driverEmployeeId, List<CompartmentLoad> loads) { this(tankerId, driverEmployeeId, loads, null); }

    public record CompartmentLoad(@NotNull UUID compartmentId,
                                  @NotNull @Positive(message = "Litres must be above zero") BigDecimal litres) {}
}
