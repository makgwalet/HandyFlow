package za.co.handyflow.platform.projects.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateResourceRequest(
        String      resourceType,  // HUMAN|EQUIPMENT|VEHICLE|SUBCONTRACTOR
        UUID        resourceId,
        String      resourceName,  // required
        String      role,
        UUID        taskId,
        BigDecimal  allocationPct,
        LocalDate   startDate,
        LocalDate   endDate,
        BigDecimal  hourlyRate,
        BigDecimal  dailyRate,
        BigDecimal  plannedHours
) {}
