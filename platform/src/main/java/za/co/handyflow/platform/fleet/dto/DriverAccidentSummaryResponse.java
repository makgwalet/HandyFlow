package za.co.handyflow.platform.fleet.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DriverAccidentSummaryResponse(
        int count,
        int withInjuries,
        int withFatalities,
        BigDecimal totalRepairCost,       // damage cost: repair costs recorded on these accidents
        BigDecimal totalExcess,           // insurance excess recorded on these accidents
        List<Item> accidents              // newest first
) {
    public record Item(UUID id, String referenceNumber, Instant occurredAt, UUID vehicleId, String status) {}
}