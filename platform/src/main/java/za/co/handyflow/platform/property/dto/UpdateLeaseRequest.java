package za.co.handyflow.platform.property.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateLeaseRequest(
        BigDecimal monthlyRent,
        LocalDate  endDate,
        Integer    paymentDay,
        BigDecimal escalationRate,
        String     notes
) {}
