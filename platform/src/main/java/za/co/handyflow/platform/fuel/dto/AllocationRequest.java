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
        @NotEmpty @Valid List<CompartmentLoad> loads
) {
    public record CompartmentLoad(@NotNull UUID compartmentId,
                                  @NotNull @Positive(message = "Litres must be above zero") BigDecimal litres) {}
}
