package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** What a client may see of a credit note: approved ones only; no preparer/approver or ledger detail. */
public record FmPortalCreditNoteResponse(
        UUID id, String creditNoteNumber, UUID invoiceId, String invoiceNumber, LocalDate issueDate, String reason,
        BigDecimal subtotal, BigDecimal vatAmount, BigDecimal total
) {}
