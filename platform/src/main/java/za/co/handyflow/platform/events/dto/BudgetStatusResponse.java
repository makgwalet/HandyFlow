package za.co.handyflow.platform.events.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record BudgetStatusResponse(
        String status,              // NOT_STARTED | DRAFT | SUBMITTED | APPROVED
        int lineCount,
        BigDecimal plannedTotal,
        String submittedByName,
        Instant submittedAt,
        String approvedByName,
        Instant approvedAt,
        BigDecimal approvedTotal,
        String rejectionReason      // set when an approver sent it back; cleared on resubmission
) {}