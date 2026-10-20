package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateAccidentRepairRequest(
        @Size(max = 255) String repairer,
        @DecimalMin("0.00") BigDecimal repairCost,
        LocalDate returnedToServiceAt
) {}
