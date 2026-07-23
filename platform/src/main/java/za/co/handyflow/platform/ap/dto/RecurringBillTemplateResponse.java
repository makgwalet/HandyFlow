package za.co.handyflow.platform.ap.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RecurringBillTemplateResponse(
        UUID       id,
        UUID       supplierId,
        String     supplierName,
        String     category,
        String     description,
        BigDecimal amount,
        BigDecimal vatAmount,
        BigDecimal totalAmount,
        String     frequency,
        int        dayOfMonth,
        int        leadDays,
        LocalDate  nextDueDate,
        UUID       lastGeneratedBillId,
        Instant    lastGeneratedAt,
        boolean    active,
        String     notes,
        Instant    createdAt
) {}