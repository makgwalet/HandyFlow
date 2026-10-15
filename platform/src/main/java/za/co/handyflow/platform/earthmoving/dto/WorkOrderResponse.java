package za.co.handyflow.platform.earthmoving.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record WorkOrderResponse(
        UUID id, UUID assetId, String number, String title, String description, String type, String priority, String status,
        UUID assignedToUserId, String assignedToName, LocalDate dueDate, boolean overdue, String source, UUID planId,
        UUID incidentId, boolean blocksRelease, Instant startedAt, Instant completedAt, BigDecimal meterHours,
        BigDecimal downtimeHours, BigDecimal labourHours, String workDone, String testResult, UUID maintenanceRecordId,
        String cancelReason, BigDecimal totalCost, List<WorkOrderLineResponse> lines, Instant createdAt) {}
