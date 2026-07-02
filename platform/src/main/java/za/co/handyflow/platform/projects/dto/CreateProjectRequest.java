package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateProjectRequest(

        @NotBlank(message = "Project name is required")
        @Size(max = 255, message = "Name must not exceed 255 characters")
        String name,

        @NotBlank(message = "Project type is required")
        String projectType,         // CONSTRUCTION|EARTHMOVING|SECURITY|EVENT|IT|GENERAL

        @Size(max = 2000, message = "Description must not exceed 2 000 characters")
        String description,

        UUID clientId,

        @Size(max = 255, message = "Client name must not exceed 255 characters")
        String clientName,

        LocalDate startDate,

        LocalDate endDate,

        @NotNull(message = "Budget total is required")
        @DecimalMin(value = "0.01", message = "Budget must be greater than zero")
        BigDecimal budgetTotal,

        @DecimalMin(value = "0.00", inclusive = true, message = "Contract value cannot be negative")
        BigDecimal contractValue,

        @Size(max = 100, message = "Contract ref must not exceed 100 characters")
        String contractRef,

        String contractType,        // FIXED_PRICE|TIME_AND_MATERIAL|COST_PLUS

        @DecimalMin(value = "0.0",   message = "Retention cannot be negative")
        @DecimalMax(value = "100.0", message = "Retention cannot exceed 100 %")
        BigDecimal retentionPct,

        @Size(max = 20,  message = "CIDB grade must not exceed 20 characters")
        String cidbGrade,

        @Size(max = 50,  message = "NHBRC number must not exceed 50 characters")
        String nhbrcNumber,

        @Size(max = 500, message = "Site address must not exceed 500 characters")
        String siteAddress,

        UUID projectManagerId,

        @Size(max = 255, message = "Project manager name must not exceed 255 characters")
        String projectManagerName,

        @Size(max = 2000, message = "Notes must not exceed 2 000 characters")
        String notes

) {}
