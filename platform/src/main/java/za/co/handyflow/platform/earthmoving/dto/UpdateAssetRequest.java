package za.co.handyflow.platform.earthmoving.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Edit an asset's details (patch E1). Status, hours, site and client change through their own actions. */
public record UpdateAssetRequest(
        @NotBlank String name,
        @NotBlank String assetType,
        String fleetNumber,
        String make,
        String model,
        Integer year,
        String serialNumber,
        String registration,
        String ownershipType,
        String hireSupplier,
        LocalDate hireStartDate,
        LocalDate hireEndDate,
        BigDecimal dailyRate,
        BigDecimal hourlyRate,
        BigDecimal serviceIntervalHours,
        String notes
) {}
