package za.co.handyflow.platform.accountant.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ClientResponse(
        UUID id,
        String entityType,
        String tradingName,
        String registeredName,
        String registrationNumber,
        String taxReferenceNumber,
        String vatNumber,
        String vatCategory,
        int yearEndMonth,
        String riskRating,
        boolean ficaCompleted,
        boolean sarsAgentAppointed,
        String tcsPin,
        LocalDate tcsPinExpiry,
        String onboardingStatus,
        String contactEmail,
        String contactPhone,
        // Computed fields
        int openDeadlines,
        int overdueDeadlines,
        BigDecimal wip,                  // unbilled WIP
        BigDecimal outstandingInvoices,
        Instant createdAt,
        // NEW: closes the audit's "client-facing deadline reminder
        // emails" gap — per-client opt-out, defaults to true.
        boolean clientDeadlineRemindersEnabled
) {
}