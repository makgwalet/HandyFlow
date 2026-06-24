package za.co.handyflow.platform.projects.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateProjectRequest(
        String      name,               // required
        String      projectType,        // CONSTRUCTION|EARTHMOVING|SECURITY|EVENT|IT|GENERAL
        String      description,
        UUID        clientId,
        String      clientName,
        LocalDate   startDate,
        LocalDate   endDate,
        BigDecimal  budgetTotal,
        BigDecimal  contractValue,
        String      contractRef,
        String      contractType,       // FIXED_PRICE|TIME_AND_MATERIAL|COST_PLUS
        BigDecimal  retentionPct,
        String      cidbGrade,
        String      nhbrcNumber,
        String      siteAddress,
        UUID        projectManagerId,
        String      projectManagerName,
        String      notes
) {}
