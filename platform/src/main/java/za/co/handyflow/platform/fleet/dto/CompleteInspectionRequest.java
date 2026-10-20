package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.Size;

public record CompleteInspectionRequest(
        @Size(max = 4000) String notes
) {}