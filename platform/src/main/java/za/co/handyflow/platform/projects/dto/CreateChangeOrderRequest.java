package za.co.handyflow.platform.projects.dto;

import java.math.BigDecimal;

public record CreateChangeOrderRequest(
        String      title,          // required
        String      description,
        String      reason,
        BigDecimal  costImpact,
        int         scheduleImpact  // days
) {}
