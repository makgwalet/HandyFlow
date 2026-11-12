package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record TankerRequest(
        @NotBlank @Size(max = 20) String registration,
        @Size(max = 80) String name,
        // One entry per compartment, in order (compartment 1, 2, ...). Fixed once created.
        @NotEmpty @Size(max = 12) List<@NotNull @Positive(message = "Compartment capacity must be above zero") BigDecimal> compartmentCapacities,
        // Optional: the vehicle in Fleet this tanker is. When set, the registration follows Fleet.
        UUID fleetVehicleId,
        // On update only: true removes an existing link. Null or false leaves it as it is.
        Boolean unlinkFleetVehicle
) {
    public TankerRequest(String registration, String name, List<BigDecimal> compartmentCapacities) {
        this(registration, name, compartmentCapacities, null, null);
    }
}
