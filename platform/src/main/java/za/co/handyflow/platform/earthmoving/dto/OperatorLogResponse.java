package za.co.handyflow.platform.earthmoving.dto;
import java.math.BigDecimal; import java.time.Instant; import java.util.UUID;
public record OperatorLogResponse(
        UUID id, UUID assetId, String operatorName, String siteName,
        Instant startedAt, Instant endedAt, BigDecimal hoursLogged,
        BigDecimal fuelUsedLitres, Instant createdAt
) {}