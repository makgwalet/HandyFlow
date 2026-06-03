package za.co.handyflow.platform.contracting.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Lightweight response for list endpoints — excludes the HTML body to avoid
 * sending megabytes of HTML on paginated list calls.
 *
 * Use ContractResponse (with body) for GET /contracts/{id} detail view.
 */
public record ContractSummaryResponse(
        UUID       id,
        String     contractNumber,
        String     title,
        String     contractType,
        String     status,
        BigDecimal valueAmount,
        String     currency,
        LocalDate  startDate,
        LocalDate  endDate,
        boolean    autoRenew,
        int        signedPartyCount,
        int        totalPartyCount,
        Instant    sentAt,
        Instant    signedAt,
        Instant    createdAt
) {}
