package za.co.handyflow.platform.fleet.dto;
import java.math.BigDecimal; import java.time.Instant;
public record EndTripRequest(
        String endLocation, Integer endOdometer,
        Instant endAt, BigDecimal fuelUsedLitres, String notes
) {}