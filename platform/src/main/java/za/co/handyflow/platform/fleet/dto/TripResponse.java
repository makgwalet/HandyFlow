package za.co.handyflow.platform.fleet.dto;
import java.math.BigDecimal; import java.time.Instant; import java.util.UUID;
public record TripResponse(
        UUID id,
        UUID vehicleId,
        String registration,   // denormalized for list views — avoid N+1
        String driverName,
        String purpose,
        String tripType,       // BUSINESS | PRIVATE — for SARS logbook
        String startLocation,
        String endLocation,
        Integer startOdometer,
        Integer endOdometer,
        Integer distanceKm,
        Instant startAt,
        Instant endAt,
        BigDecimal fuelUsedLitres,
        String status,         // ACTIVE | COMPLETED | CANCELLED
        String notes,
        Instant createdAt,
        UUID driverId          // NEW (chunk 6): linked Driver record, null for free-text-only trips
) {}