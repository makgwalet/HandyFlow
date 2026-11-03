package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * PATCH-style update: null means "leave unchanged".
 * startDate can only change before the baseline is locked (PLANNING / PROPOSED).
 */
public record UpdateProjectRequest(
        @Size(max = 255) String name,
        @Size(max = 2000) String description,
        LocalDate   startDate,
        LocalDate   endDate,
        @DecimalMin(value = "0.01", message = "Budget must be greater than zero")
        BigDecimal  budgetTotal,
        @DecimalMin(value = "0.00", message = "Contract value cannot be negative")
        BigDecimal  contractValue,
        @Size(max = 100) String contractRef,
        String      contractType,
        @DecimalMin(value = "0.0") @DecimalMax(value = "100.0")
        BigDecimal  retentionPct,
        @Size(max = 20) String cidbGrade,
        @Size(max = 50) String nhbrcNumber,
        @Size(max = 500) String siteAddress,
        UUID        projectManagerId,
        @Size(max = 255) String projectManagerName,
        UUID        sponsorId,
        @Size(max = 255) String sponsorName,
        @Size(max = 4000) String objectives,
        @Size(max = 4000) String scopeStatement,
        @Size(max = 4000) String businessCase,
        @Size(max = 2000) String notes
) {}