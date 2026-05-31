package za.co.handyflow.platform.earthmoving.dto;
import java.math.BigDecimal; import java.time.Instant; import java.util.UUID;
public record MaintenanceResponse(
        UUID id, UUID assetId, String type, String description,
        Instant performedAt, BigDecimal hoursAtService,
        BigDecimal cost, String supplier, String invoiceRef,
        Instant createdAt
) {}