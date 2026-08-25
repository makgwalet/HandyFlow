package za.co.handyflow.platform.clinic.dto.billing;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * FIX: backlog 1.6 — bankAccountId is new and deliberately optional.
 * Without it, the GL posting added to ClinicBillingService.recordPayment()
 * can't post a directed "Debit Bank / Credit AR" journal (there's
 * nothing to debit) — it logs clearly and skips posting rather than
 * guessing a default account. Same exact gap and same fix already
 * applied to invoicing.dto.RecordPaymentRequest — see that class's own
 * Javadoc for the full reasoning.
 */
public record RecordPaymentRequest(
        @NotNull UUID patientId,
        @NotNull String method,          // CASH, EFT, CARD — informational only
        @NotNull @Positive BigDecimal amount,
        String reference,
        String notes,
        UUID bankAccountId               // NEW — nullable; see class Javadoc
) {}