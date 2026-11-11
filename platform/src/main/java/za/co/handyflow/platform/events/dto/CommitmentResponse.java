package za.co.handyflow.platform.events.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CommitmentResponse(
        UUID id,
        UUID budgetLineId,
        String category,
        UUID vendorId,
        String vendorName,
        String description,
        BigDecimal amount,
        String status,
        String quoteReference,
        String poReference,
        String contractReference,
        String deliverable,
        LocalDate dueDate,
        BigDecimal depositAmount,
        LocalDate depositDueDate,
        String approvedByName,
        Instant approvedAt,
        String overBudgetReason,
        String cancelledReason,
        BigDecimal invoiced,        // active INVOICE entries against this commitment
        BigDecimal paid,            // active PAYMENT entries against this commitment
        Instant createdAt
) {}