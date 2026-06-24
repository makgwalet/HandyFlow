package za.co.handyflow.platform.projects.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateBudgetLineRequest(
        String      category,       // LABOUR|MATERIALS|SUBCONTRACT|EQUIPMENT|OVERHEAD|CONTINGENCY
        String      description,    // required
        UUID        phaseId,
        BigDecimal  budgetedAmount, // required
        boolean     isProvisional,
        boolean     isPrimeCost
) {}
