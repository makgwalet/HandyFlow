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
        UUID       journalEntryId,
        UUID       paymentJournalId,
        // NEW: maker-checker fields — both null unless this bill crossed
        // the second-approval threshold. firstApprovedBy lets the frontend
        // disable the Approve button for whoever already gave the first
        // approval, matching the backend's own SAME_APPROVER guard (a UX
        // nicety, not a substitute for that guard — the backend still
        // enforces it either way).
        UUID       firstApprovedBy,
        Instant    firstApprovedAt,
        Instant    paidAt,
        Instant    createdAt,
        // NEW: possible-duplicate warning — only ever populated by
        // createBill() when a same-supplier, same-amount bill already
        // exists within a tight date window. Every other path (get/list)
        // leaves this null. See ApBillRepository.findPossibleDuplicates()
        // for why this is a warning, not a hard block.
        String     possibleDuplicateWarning
) {}