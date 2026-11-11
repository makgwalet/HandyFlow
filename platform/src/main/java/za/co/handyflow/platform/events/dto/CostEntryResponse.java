package za.co.handyflow.platform.events.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CostEntryResponse(
        UUID id,
        UUID budgetLineId,
        UUID commitmentId,
        String kind,
        boolean deposit,
        BigDecimal amount,
        LocalDate entryDate,
        String reference,
        String description,
        String sourceModule,
        String sourceRef,
        boolean reconciled,         // true when linked to the authoritative accounting record
        String status,              // ACTIVE | VOIDED
        String voidReason,
        String recordedByName,
        Instant createdAt
) {}