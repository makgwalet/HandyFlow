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
        // NEW: was previously computed on the frontend from a `comments`
        // array that never existed on this response at all — the
        // "unresolved amendments" badge on each contract row silently
        // showed 0 for every contract, always. A count, not a full comments
        // list, since this is a paginated list endpoint (potentially
        // hundreds of rows) — see ContractingService.getContracts() for how
        // this is batch-computed in one query rather than per-row.
        int        unresolvedAmendmentCount,
        Instant    sentAt,
        Instant    signedAt,
        Instant    createdAt
) {}
