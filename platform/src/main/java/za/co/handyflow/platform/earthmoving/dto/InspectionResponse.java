package za.co.handyflow.platform.earthmoving.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InspectionResponse(UUID id, UUID assetId, String machine, UUID inspectorUserId, Instant performedAt,
                                 BigDecimal meterReading, String result, String notes, List<Item> items, String inspectionType) {
    public record Item(String item, String outcome, String comment, UUID incidentId) {}
}
