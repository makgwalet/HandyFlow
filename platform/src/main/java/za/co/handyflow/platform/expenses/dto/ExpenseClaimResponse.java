package za.co.handyflow.platform.expenses.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ExpenseClaimResponse(
        UUID id,
        String claimNumber,
        UUID employeeId,
        String employeeName,
        LocalDate claimDate,
        String category,
        String description,
        BigDecimal amount,
        String currency,
        String receiptUrl,
        String status,
        String rejectionReason,
        UUID journalEntryId,
        String notes,
        Instant approvedAt,
        Instant reimbursedAt,
        Instant createdAt
) {}