package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * What a client contact may see of an invoice. Deliberately EXCLUDES who prepared/approved it, ledger
 * posting status and errors, and internal timestamps. Clients only ever see SENT/PARTIAL/PAID/CREDITED invoices; creditedAmount explains why balance is lower than total minus payments.
 */
public record FmPortalInvoiceResponse(
        UUID id, String invoiceNumber, LocalDate periodStart, LocalDate periodEnd, LocalDate issueDate,
        LocalDate dueDate, BigDecimal subtotal, BigDecimal vatAmount, BigDecimal total,
        BigDecimal amountPaid, BigDecimal creditedAmount, BigDecimal balance, String status
) {}