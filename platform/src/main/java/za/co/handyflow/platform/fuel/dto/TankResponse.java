package za.co.handyflow.platform.fuel.dto;
import java.math.BigDecimal; import java.time.Instant; import java.util.UUID;
public record TankResponse(
        UUID id, String name, String fuelType,
        BigDecimal capacityLitres, BigDecimal currentLitres,
        BigDecimal fillPercentage, boolean low,
        String location, Instant createdAt
) {}