package za.co.handyflow.platform.ap.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BillResponse(
        UUID       id,
        UUID       supplierId,
        String     supplierName,
        String     billNumber,
        LocalDate  billDate,
        LocalDate  dueDate,
        String     category,
        String     description,
        BigDecimal amount,
        BigDecimal vatAmount,
        BigDecimal totalAmount,
        String     currency,
        String     status,
        boolean    overdue,
        int        daysUntilDue,
        boolean    hasAttachment,
        boolean    hasPop,
        String     paymentRef,
        UUID       batchId,
        String     notes,
        Instant    paidAt,
        Instant    createdAt
) {}
