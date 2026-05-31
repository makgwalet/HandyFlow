package za.co.handyflow.platform.property.dto;
import java.math.BigDecimal; import java.time.Instant;
import java.util.List; import java.util.UUID;
public record UnitResponse(
        UUID id, UUID propertyId, String unitNumber, String unitType,
        Integer floorNumber, BigDecimal sizeSqm, BigDecimal baseRent,
        BigDecimal depositAmount, String status, boolean furnished,
        List<String> amenities, String notes, Instant createdAt
) {}