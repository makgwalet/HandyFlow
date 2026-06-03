package za.co.handyflow.platform.accountant.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// ── REQUESTS ──────────────────────────────────────────────────────────────────

// ── RESPONSES ─────────────────────────────────────────────────────────────────

public record JournalResponse(
        UUID id,
        UUID clientId,
        UUID periodId,
        String reference,
        String description,
        String journalType,
        String status,
        LocalDate journalDate,
        BigDecimal totalDebits,
        BigDecimal totalCredits,
        boolean balanced,
        List<JournalLineResponse> lines,
        Instant createdAt
) {}

