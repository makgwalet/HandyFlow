package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Everything a technician needs after scanning an asset's QR code. {@code openWorkOrders} only lists the ones the
 * caller may see; {@code nextPpmDue} is the earliest due date among the asset's active PPM schedules.
 */
public record FmAssetLookupResponse(FmAssetResponse asset, String qrPayload, List<OpenWorkOrder> openWorkOrders, LocalDate nextPpmDue) {
    public record OpenWorkOrder(UUID id, String workOrderNumber, String status, String priority, String description, LocalDate scheduledDate) {}
}
