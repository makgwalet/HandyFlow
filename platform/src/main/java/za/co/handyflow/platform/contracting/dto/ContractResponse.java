package za.co.handyflow.platform.contracting.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Full contract response including body HTML.
 *
 * FIX §30: Original ContractResponse was missing the body field entirely.
 * The frontend cannot display contract content without it.
 * The body is included in detail responses; list responses use ContractSummaryResponse
 * (without body) to avoid sending megabytes of HTML on every list call.
 */
public record ContractResponse(
        UUID       id,
        String     contractNumber,
        String     title,
        String     contractType,
        String     status,
        String     body,                // FIX: was missing — HTML body of the contract
        String     bodyHash,            // SHA-256 of body at time of sending
        BigDecimal valueAmount,
        String     currency,
        LocalDate  startDate,
        LocalDate  endDate,
        boolean    autoRenew,
        int        renewalNoticeDays,
        String     notes,
        Instant    sentAt,
        Instant    signedAt,
        Instant    terminatedAt,
        String     terminationReason,
        List<PartyResponse> parties,
        // NEW: was missing entirely — comments could be posted (once the
        // staff-comment endpoint exists) but had nowhere to be read back
        // from on this response, so they'd never actually appear on screen
        // even after a successful post and a refetch.
        List<CommentView> comments,
        Instant    createdAt,
        Instant    updatedAt
) {}
