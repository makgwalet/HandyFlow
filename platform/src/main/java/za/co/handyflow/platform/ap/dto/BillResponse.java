package za.co.handyflow.platform.ap.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * FIX: backlog 1.1b — adds rejectionReason as a new trailing field, so
 * the person editing a rejected bill can actually see why it was
 * rejected before resubmitting it.
 * <p>
 * ⚠ Field order/names for everything before rejectionReason are
 * inferred from ApService.toBillResponse()'s own confirmed positional
 * constructor call, not independently verified against this record's
 * original declaration.
 */
public record BillResponse(
        UUID id, UUID supplierId, String supplierName,
        String billNumber, LocalDate billDate, LocalDate dueDate,
        String category, String description,
        BigDecimal amount, BigDecimal vatAmount, BigDecimal totalAmount, String currency,
        String status, boolean overdue, int daysUntilDue,
        boolean hasAttachment, boolean hasPop,
        String paymentRef, UUID batchId, String notes,
        UUID journalEntryId, UUID paymentJournalId,
        UUID firstApprovedBy, Instant firstApprovedAt,
        Instant paidAt, Instant createdAt, String possibleDuplicateWarning,
        String rejectionReason
) {}