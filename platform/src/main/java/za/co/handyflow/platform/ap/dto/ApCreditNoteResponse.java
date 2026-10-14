package za.co.handyflow.platform.ap.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ApCreditNoteResponse(
        UUID id, String creditNoteNumber, UUID supplierId, String supplierName,
        UUID billId, String billNumber, String category, LocalDate creditDate, String reason,
        BigDecimal amount, BigDecimal vatAmount, BigDecimal totalAmount,
        BigDecimal allocated, BigDecimal available,
        String status, String rejectionReason, UUID journalEntryId,
        UUID createdBy, UUID decidedBy, Instant decidedAt, Instant createdAt,
        List<Allocation> allocations
) {
    public record Allocation(UUID billId, String billNumber, BigDecimal amount, Instant allocatedAt) {}
}
