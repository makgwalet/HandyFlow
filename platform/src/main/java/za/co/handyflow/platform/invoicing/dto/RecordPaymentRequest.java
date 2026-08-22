package za.co.handyflow.platform.invoicing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * FIX: backlog 1.6 — bankAccountId is new and deliberately optional.
 * Without it, InvoicingAccountingEventHandler can't post a directed
 * "Debit Bank / Credit AR" journal (there's nothing to debit) — it logs
 * clearly and skips posting rather than guessing a default account. See
 * InvoicePaymentRecordedEvent's own Javadoc for the full reasoning.
 */
public record RecordPaymentRequest(
        @NotNull @Positive BigDecimal amountPaid,
        LocalDate paidDate,           // null = today
        String paymentMethod,         // EFT, CASH, CARD — informational only
        String reference,             // bank reference, cheque number, etc.
        UUID bankAccountId            // NEW — nullable; see class Javadoc
) {}