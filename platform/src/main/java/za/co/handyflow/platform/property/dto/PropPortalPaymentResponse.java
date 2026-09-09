package za.co.handyflow.platform.property.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PropPortalPaymentResponse(
        UUID id,
        int periodYear,
        int periodMonth,
        BigDecimal amountDue,
        BigDecimal amountPaid,
        LocalDate dueDate,
        LocalDate paidDate,
        String status
) {}
