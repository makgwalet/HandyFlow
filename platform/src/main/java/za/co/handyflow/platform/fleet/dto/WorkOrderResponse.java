package za.co.handyflow.platform.fleet.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** List-view summary of a work order. */
public record WorkOrderResponse(
        UUID id,
        String referenceNumber,
        UUID vehicleId,
        String registration,
        String type,
        String title,
        String priority,
        String status,
        UUID assignedTechnicianId,
        String assignedTechnicianName,
        String supplier,
        LocalDate scheduledFor,
        BigDecimal quotedAmount,
        BigDecimal actualTotal,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt
) {}