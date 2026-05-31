package za.co.handyflow.platform.fleet.dto;
import java.math.BigDecimal; import java.time.Instant; import java.util.UUID;
public record TripResponse(
        UUID id, UUID vehicleId, String driverName, String purpose,
        String startLocation, String endLocation,
        Integer startOdometer, Integer endOdometer,
        Integer distanceKm, Instant startAt, Instant endAt,
        BigDecimal fuelUsedLitres, Instant createdAt
) {}