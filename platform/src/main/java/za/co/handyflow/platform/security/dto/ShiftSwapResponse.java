package za.co.handyflow.platform.security.dto;

import java.time.Instant;
import java.util.UUID;

public record ShiftSwapResponse(
        UUID id,
        UUID originalShiftId,
        String shiftSummary,            // e.g. "Sandton City Mall — 2026-07-01 06:00–18:00"
        UUID requestingGuardId,
        String requestingGuardName,
        UUID proposedGuardId,
        String proposedGuardName,
        String status,
        Instant proposedAcceptedAt,
        Instant requestedAt,
        UUID decidedBy,
        String decidedByName,
        Instant decidedAt,
        String reason,
        String rejectionReason,
        Boolean validationPassed,
        String validationNotes,
        Instant createdAt
) {}
