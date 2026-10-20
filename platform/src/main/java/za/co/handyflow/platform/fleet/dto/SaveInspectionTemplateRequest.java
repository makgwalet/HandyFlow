package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.fleet.domain.model.InspectionType;

import java.util.List;

/** Used for both create and (full-replace) update of a template. {@code active} defaults to true. */
public record SaveInspectionTemplateRequest(
        @NotBlank @Size(max = 150) String name,
        @NotNull InspectionType inspectionType,
        String description,
        @Min(1) @Max(3650) Integer intervalDays,      // schedule: flag vehicles with none completed within N days
        @Size(max = 50) String appliesToVehicleType,  // limit the schedule to one vehicle type (e.g. TRUCK)
        boolean signatureRequired,
        Boolean active,
        @NotEmpty @Size(max = 200) List<@Valid TemplateItemRequest> items
) {}