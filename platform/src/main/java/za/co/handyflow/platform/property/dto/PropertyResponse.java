package za.co.handyflow.platform.property.dto;
import java.math.BigDecimal; import java.time.Instant;
import java.util.List; import java.util.Map; import java.util.UUID;
public record PropertyResponse(
        UUID id, String name, String propertyType,
        Map<String, String> address, String description,
        UUID customerId, BigDecimal purchasePrice, BigDecimal marketValue,
        int totalUnits, long vacantUnits, long occupiedUnits,
        List<UnitResponse> units, Instant createdAt
) {}