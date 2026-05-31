package za.co.handyflow.platform.fleet.dto;
import java.math.BigDecimal; import java.time.Instant;
import java.time.LocalDate; import java.util.UUID;
public record VehicleResponse(
        UUID id, String registration, String make, String model,
        Integer year, String colour, String vehicleType,
        String status, String fuelType,
        LocalDate licenceDiscExpiry, LocalDate roadworthyExpiry,
        LocalDate insuranceExpiry,
        Integer currentOdometer, Integer lastServiceKm,
        Integer serviceIntervalKm, boolean dueForService,
        boolean licenceExpiringSoon, boolean roadworthyExpiringSoon,
        BigDecimal dailyRate, String notes, Instant createdAt
) {}