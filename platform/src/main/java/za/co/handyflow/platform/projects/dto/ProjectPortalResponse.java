package za.co.handyflow.platform.projects.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ProjectPortalResponse(
        String      projectNumber,
        String      name,
        String      clientName,
        String      status,
        String      health,
        LocalDate   startDate,
        LocalDate   endDate,
        BigDecimal  budgetTotal,
        BigDecimal  completionPct,
        List<TaskResponse>    milestones,
        List<SnagResponse>    openSnags,
        List<RiskResponse>    redRisks
) {}
