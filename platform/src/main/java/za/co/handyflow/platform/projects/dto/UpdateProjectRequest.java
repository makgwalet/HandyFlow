package za.co.handyflow.platform.projects.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record UpdateProjectRequest(
        String      name,
        String      description,
        LocalDate   startDate,
        LocalDate   endDate,
        BigDecimal  budgetTotal,
        BigDecimal  contractValue,
        String      contractRef,
        String      cidbGrade,
        String      nhbrcNumber,
        String      siteAddress,
        UUID        projectManagerId,
        String      projectManagerName,
        String      notes
) {}
