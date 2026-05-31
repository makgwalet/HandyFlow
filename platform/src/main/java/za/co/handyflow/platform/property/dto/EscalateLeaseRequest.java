package za.co.handyflow.platform.property.dto;

import java.math.BigDecimal;

public record EscalateLeaseRequest(
        BigDecimal escalationPercent,
        BigDecimal newMonthlyRent
) {}
