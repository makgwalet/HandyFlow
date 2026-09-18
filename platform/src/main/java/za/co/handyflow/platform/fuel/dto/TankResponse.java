package za.co.handyflow.platform.fuel.dto;
import java.math.BigDecimal; import java.time.Instant; import java.util.UUID;
public record TankResponse(
        UUID id, String name, String fuelType,
        BigDecimal capacityLitres, BigDecimal currentLitres,
        BigDecimal fillPercentage, boolean low,
        String location, Instant createdAt,
        // FIX: closes the confirmed "lowThresholdPct silently dropped"
        // gap — appended at the end, matching this session's own
        // established convention for extending an existing response.
        BigDecimal lowThresholdPct
) {}