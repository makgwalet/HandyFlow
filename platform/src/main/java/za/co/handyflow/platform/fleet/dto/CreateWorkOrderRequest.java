package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.fleet.domain.model.WorkOrderPriority;
import za.co.handyflow.platform.fleet.domain.model.WorkOrderType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Raise a work order. Link it to a fleet issue ({@code issueId}) and/or an accident case
 * ({@code accidentId}); the title then defaults to the issue's, and priority to the issue's severity.
 */
public record CreateWorkOrderRequest(
        @NotNull UUID vehicleId,
        UUID issueId,
        UUID accidentId,
        @NotNull WorkOrderType type,
        @Size(max = 255) String title,            // required unless issueId is given
        String description,
        WorkOrderPriority priority,
        UUID technicianId,
        @Size(max = 255) String supplier,
        LocalDate scheduledFor,
        @PositiveOrZero Integer odometerIn,
        Instant vehicleOffRoadFrom
) {}