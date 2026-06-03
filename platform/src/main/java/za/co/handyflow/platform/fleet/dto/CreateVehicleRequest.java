package za.co.handyflow.platform.fleet.dto;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal; import java.time.LocalDate; import java.util.UUID;
public record CreateVehicleRequest(
        @NotBlank String registration,
        @NotBlank String make,
        @NotBlank String model,
        Integer year,
        String colour,
        String vin,
        @NotBlank String vehicleType,
        String fuelType,
        LocalDate licenceDiscExpiry,
        LocalDate roadworthyExpiry,
        LocalDate insuranceExpiry,
        BigDecimal dailyRate,
        BigDecimal tankCapacityLitres,
        Integer serviceIntervalKm,     // custom interval — defaults to 10000
        Integer serviceIntervalDays,   // time-based interval — e.g. 180 days
        String assignedDriverName,
        String notes,
        UUID customerId
) {}