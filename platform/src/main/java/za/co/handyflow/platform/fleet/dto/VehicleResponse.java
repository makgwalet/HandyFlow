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
        BigDecimal dailyRate, String notes, Instant createdAt,
        String assignedDriverName, UUID assignedDriverId,

        // ── NEW (appended so existing field order is untouched) ───────────
        String vin,
        String engineNumber,
        String variant,
        String bodyType,
        String transmission,
        String driveType,
        Integer axleCount,
        Integer gvmKg,
        Integer tareKg,
        Integer payloadKg,
        Integer gcmKg,
        BigDecimal engineHours,
        BigDecimal tankCapacityLitres,
        Integer serviceIntervalDays,
        LocalDate lastServiceDate,
        LocalDate acquisitionDate,
        String acquisitionMethod,
        BigDecimal purchasePrice,
        BigDecimal currentBookValue,
        BigDecimal currentEstimatedValue,
        LocalDate valuedAt,

        // ── NEW (chunk 5) ─────────────────────────────────────────────────
        String lifecycleStatus
) {}