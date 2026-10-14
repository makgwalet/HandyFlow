package za.co.handyflow.platform.accounting.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BankReconciliationResponse(
        UUID id, UUID bankAccountId, LocalDate periodEnd,
        BigDecimal statementBalance, BigDecimal bookBalance,
        int unmatchedBookCount, BigDecimal unmatchedBookNet, BigDecimal difference,
        String note, String status,
        UUID signedBy, Instant signedAt, UUID reopenedBy, Instant reopenedAt, String reopenReason
) {}
