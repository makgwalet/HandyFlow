package za.co.handyflow.platform.facilities.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Everything a technician needs after scanning an asset's QR code. {@code openWorkOrders} only lists the ones the
 * caller may see; {@code nextPpmDue} is the earliest due date among the asset's active PPM schedules.
 */
public record AssetLookupResponse(
        AssetResponse asset, String qrPayload, List<OpenWorkOrder> openWorkOrders, long openCorrectiveActions,
        LastInspection lastInspection, LocalDate nextPpmDue
) {
    public record OpenWorkOrder(UUID id, String workOrderNumber, String status, String priority, String description, LocalDate scheduledDate) {}

    public record LastInspection(UUID id, String templateName, String result, Instant completedAt) {}
}
