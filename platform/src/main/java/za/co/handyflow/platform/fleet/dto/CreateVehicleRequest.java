package za.co.handyflow.platform.fleet.dto;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal; import java.time.LocalDate; import java.util.UUID;
public record CreateVehicleRequest(
        @NotBlank String registration, @NotBlank String make,
        @NotBlank String model, Integer year, String colour,
        String vin, @NotBlank String vehicleType,
        String fuelType, LocalDate licenceDiscExpiry,
        LocalDate roadworthyExpiry, LocalDate insuranceExpiry,
        BigDecimal dailyRate, BigDecimal tankCapacityLitres,
        UUID customerId
) {}