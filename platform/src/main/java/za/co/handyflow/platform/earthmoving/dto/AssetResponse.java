package za.co.handyflow.platform.earthmoving.dto;
import java.math.BigDecimal; import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
public record AssetResponse(
        UUID id,
        String name,
        String fleetNumber,
        String assetType,
        String make,
        String model,
        Integer year,
        String serialNumber,
        String registration,
        String ownershipType,       // OWN | HIRED_IN | HIRED_OUT
        String hireSupplier,
        LocalDate hireStartDate,
        LocalDate hireEndDate,
        String status,              // AVAILABLE | DEPLOYED | MAINTENANCE | BREAKDOWN | HIRED_OUT | RETIRED
        String currentSite,
        String currentClient,
        BigDecimal dailyRate,
        BigDecimal hourlyRate,
        BigDecimal currentHours,
        BigDecimal lastServiceHours,
        BigDecimal serviceIntervalHours,
        boolean dueForService,
        String notes,
        Instant createdAt
) {}