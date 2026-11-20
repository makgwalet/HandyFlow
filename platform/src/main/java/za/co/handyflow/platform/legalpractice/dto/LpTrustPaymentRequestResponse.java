package za.co.handyflow.platform.legalpractice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LpTrustPaymentRequestResponse(
        UUID id,
        UUID clientId,
        String clientName,
        UUID matterId,
        String paymentType,
        BigDecimal amount,
        LocalDate transactionDate,
        UUID invoiceId,
        String payee,
        String reference,
        String notes,
        String status,
        UUID requestedBy,
        String requestedByName,
        Instant requestedAt,
        String decidedByName,
        Instant decidedAt,
        String decisionReason,
        boolean selfApproved,
        UUID resultTransactionId
) {}
