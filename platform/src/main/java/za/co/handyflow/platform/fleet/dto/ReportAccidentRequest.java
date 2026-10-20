package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReportAccidentRequest(
        @NotNull UUID vehicleId,
        @NotNull @Valid AccidentDetailsRequest details
) {}
