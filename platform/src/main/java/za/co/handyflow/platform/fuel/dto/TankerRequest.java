package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public record TankerRequest(
        @NotBlank @Size(max = 20) String registration,
        @Size(max = 80) String name,
        // One entry per compartment, in order (compartment 1, 2, ...). Fixed once created.
        @NotEmpty @Size(max = 12) List<@NotNull @Positive(message = "Compartment capacity must be above zero") BigDecimal> compartmentCapacities
) {}
