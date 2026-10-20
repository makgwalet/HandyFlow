package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.fleet.domain.model.WorkOrderLineType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One actual cost line. For LABOUR, quantity is hours, and {@code unitCost} may be omitted to
 * use the technician's hourly rate.
 */
public record SaveWorkOrderLineRequest(
        @NotNull WorkOrderLineType lineType,
        @NotBlank @Size(max = 500) String description,
        @Size(max = 100) String partNumber,
        @NotNull @Positive BigDecimal quantity,
        @PositiveOrZero BigDecimal unitCost,
        UUID technicianId
) {}