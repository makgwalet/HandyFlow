package za.co.handyflow.platform.earthmoving.dto;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
public record CreateAssetRequest(
        @NotBlank String name,
        @NotBlank String assetType,
        String fleetNumber,          // e.g. "D9-001" — identifies one machine in a fleet
        String make,
        String model,
        Integer year,
        String serialNumber,
        String registration,
        String ownershipType,        // OWN | HIRED_IN | HIRED_OUT  (defaults to OWN)
        String hireSupplier,         // HIRED_IN only: who owns the machine
        LocalDate hireStartDate,     // HIRED_IN/HIRED_OUT: hire period start
        LocalDate hireEndDate,       // HIRED_IN/HIRED_OUT: hire period end
        BigDecimal dailyRate,
        BigDecimal hourlyRate,
        String notes,
        String meterType             // HOURS | KILOMETRES (defaults to HOURS)
) {
    /** The form without a meter type: hours. */
    public CreateAssetRequest(String name, String assetType, String fleetNumber, String make, String model, Integer year,
                              String serialNumber, String registration, String ownershipType, String hireSupplier,
                              LocalDate hireStartDate, LocalDate hireEndDate, BigDecimal dailyRate, BigDecimal hourlyRate, String notes) {
        this(name, assetType, fleetNumber, make, model, year, serialNumber, registration, ownershipType, hireSupplier,
                hireStartDate, hireEndDate, dailyRate, hourlyRate, notes, null);
    }
}