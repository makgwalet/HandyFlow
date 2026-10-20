package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.fleet.domain.model.WorkOrderPriority;
import za.co.handyflow.platform.fleet.domain.model.WorkOrderType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Full replace of the editable details; allowed until the order is COMPLETED or CANCELLED. */
public record UpdateWorkOrderRequest(
        @NotNull WorkOrderType type,
        @NotBlank @Size(max = 255) String title,
        String description,
        WorkOrderPriority priority,
        UUID technicianId,
        @Size(max = 255) String supplier,
        LocalDate scheduledFor,
        @PositiveOrZero Integer odometerIn,
        Instant vehicleOffRoadFrom
) {}